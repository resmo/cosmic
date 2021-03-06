package com.cloud.storage.snapshot;

import com.cloud.configuration.Config;
import com.cloud.engine.subsystem.api.storage.DataStore;
import com.cloud.engine.subsystem.api.storage.DataStoreManager;
import com.cloud.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.Event;
import com.cloud.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.State;
import com.cloud.engine.subsystem.api.storage.SnapshotDataFactory;
import com.cloud.engine.subsystem.api.storage.SnapshotInfo;
import com.cloud.engine.subsystem.api.storage.SnapshotResult;
import com.cloud.engine.subsystem.api.storage.SnapshotService;
import com.cloud.engine.subsystem.api.storage.StrategyPriority;
import com.cloud.engine.subsystem.api.storage.VolumeInfo;
import com.cloud.framework.config.dao.ConfigurationDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.storage.CreateSnapshotPayload;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.Snapshot;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StoragePoolStatus;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.command.CreateObjectAnswer;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.datastore.db.SnapshotDataStoreDao;
import com.cloud.storage.datastore.db.SnapshotDataStoreVO;
import com.cloud.storage.to.SnapshotObjectTO;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.db.DB;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.InvalidParameterValueException;
import com.cloud.utils.fsm.NoTransitionException;

import javax.inject.Inject;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class XenserverSnapshotStrategy extends SnapshotStrategyBase {
    private static final Logger s_logger = LoggerFactory.getLogger(XenserverSnapshotStrategy.class);

    @Inject
    SnapshotService snapshotSvr;
    @Inject
    DataStoreManager dataStoreMgr;
    @Inject
    SnapshotDataStoreDao snapshotStoreDao;
    @Inject
    ConfigurationDao configDao;
    @Inject
    SnapshotDao snapshotDao;
    @Inject
    VolumeDao volumeDao;
    @Inject
    SnapshotDataFactory snapshotDataFactory;

    @Override
    public boolean deleteSnapshot(final Long snapshotId) {
        final SnapshotVO snapshotVO = snapshotDao.findById(snapshotId);

        if (snapshotVO.getState() == Snapshot.State.Allocated) {
            snapshotDao.remove(snapshotId);
            return true;
        }

        if (snapshotVO.getState() == Snapshot.State.Destroyed) {
            return true;
        }

        if (Snapshot.State.Error.equals(snapshotVO.getState())) {
            final List<SnapshotDataStoreVO> storeRefs = snapshotStoreDao.findBySnapshotId(snapshotId);
            for (final SnapshotDataStoreVO ref : storeRefs) {
                snapshotStoreDao.expunge(ref.getId());
            }
            snapshotDao.remove(snapshotId);
            return true;
        }

        if (snapshotVO.getState() == Snapshot.State.CreatedOnPrimary) {
            s_logger.debug("delete snapshot on primary storage:");
            snapshotVO.setState(Snapshot.State.Destroyed);
            snapshotDao.update(snapshotId, snapshotVO);
            return true;
        }

        if (!Snapshot.State.BackedUp.equals(snapshotVO.getState()) && !Snapshot.State.Error.equals(snapshotVO.getState())) {
            throw new InvalidParameterValueException("Can't delete snapshotshot " + snapshotId + " due to it is in " + snapshotVO.getState() + " Status");
        }

        // first mark the snapshot as destroyed, so that ui can't see it, but we
        // may not destroy the snapshot on the storage, as other snapshots may
        // depend on it.
        final SnapshotInfo snapshotOnImage = snapshotDataFactory.getSnapshot(snapshotId, DataStoreRole.Image);
        if (snapshotOnImage == null) {
            s_logger.debug("Can't find snapshot on backup storage, delete it in db");
            snapshotDao.remove(snapshotId);
            return true;
        }

        final SnapshotObject obj = (SnapshotObject) snapshotOnImage;
        try {
            obj.processEvent(Snapshot.Event.DestroyRequested);
        } catch (final NoTransitionException e) {
            s_logger.debug("Failed to set the state to destroying: ", e);
            return false;
        }

        try {
            final boolean result = deleteSnapshotChain(snapshotOnImage);
            obj.processEvent(Snapshot.Event.OperationSucceeded);
            if (result) {
                //snapshot is deleted on backup storage, need to delete it on primary storage
                final SnapshotDataStoreVO snapshotOnPrimary = snapshotStoreDao.findBySnapshot(snapshotId, DataStoreRole.Primary);
                if (snapshotOnPrimary != null) {
                    snapshotOnPrimary.setState(State.Destroyed);
                    snapshotStoreDao.update(snapshotOnPrimary.getId(), snapshotOnPrimary);
                }
            }
        } catch (final Exception e) {
            s_logger.debug("Failed to delete snapshot: ", e);
            try {
                obj.processEvent(Snapshot.Event.OperationFailed);
            } catch (final NoTransitionException e1) {
                s_logger.debug("Failed to change snapshot state: " + e.toString());
            }
            return false;
        }

        return true;
    }

    protected boolean deleteSnapshotChain(SnapshotInfo snapshot) {
        s_logger.debug("delete snapshot chain for snapshot: " + snapshot.getId());
        boolean result = false;
        boolean resultIsSet = false;   //need to track, the snapshot itself is deleted or not.
        try {
            while (snapshot != null &&
                    (snapshot.getState() == Snapshot.State.Destroying || snapshot.getState() == Snapshot.State.Destroyed || snapshot.getState() == Snapshot.State.Error)) {
                final SnapshotInfo child = snapshot.getChild();

                if (child != null) {
                    s_logger.debug("the snapshot has child, can't delete it on the storage");
                    break;
                }
                s_logger.debug("Snapshot: " + snapshot.getId() + " doesn't have children, so it's ok to delete it and its parents");
                final SnapshotInfo parent = snapshot.getParent();
                boolean deleted = false;
                if (parent != null) {
                    if (parent.getPath() != null && parent.getPath().equalsIgnoreCase(snapshot.getPath())) {
                        //NOTE: if both snapshots share the same path, it's for xenserver's empty delta snapshot. We can't delete the snapshot on the backend, as parent snapshot
                        // still reference to it
                        //Instead, mark it as destroyed in the db.
                        s_logger.debug("for empty delta snapshot, only mark it as destroyed in db");
                        snapshot.processEvent(Event.DestroyRequested);
                        snapshot.processEvent(Event.OperationSuccessed);
                        deleted = true;
                        if (!resultIsSet) {
                            result = true;
                            resultIsSet = true;
                        }
                    }
                }
                if (!deleted) {
                    final boolean r = snapshotSvr.deleteSnapshot(snapshot);
                    if (r) {
                        // delete snapshot in cache if there is
                        final List<SnapshotInfo> cacheSnaps = snapshotDataFactory.listSnapshotOnCache(snapshot.getId());
                        for (final SnapshotInfo cacheSnap : cacheSnaps) {
                            s_logger.debug("Delete snapshot " + snapshot.getId() + " from image cache store: " + cacheSnap.getDataStore().getName());
                            cacheSnap.delete();
                        }
                    }
                    if (!resultIsSet) {
                        result = r;
                        resultIsSet = true;
                    }
                }
                snapshot = parent;
            }
        } catch (final Exception e) {
            s_logger.debug("delete snapshot failed: ", e);
        }
        return result;
    }

    @Override
    public StrategyPriority canHandle(final Snapshot snapshot, final SnapshotOperation op) {
        if (SnapshotOperation.REVERT.equals(op)) {
            final long volumeId = snapshot.getVolumeId();
            final VolumeVO volumeVO = volumeDao.findById(volumeId);

            if (volumeVO != null && ImageFormat.QCOW2.equals(volumeVO.getFormat())) {
                return StrategyPriority.DEFAULT;
            }

            return StrategyPriority.CANT_HANDLE;
        }

        return StrategyPriority.DEFAULT;
    }

    @Override
    @DB
    public SnapshotInfo takeSnapshot(SnapshotInfo snapshot) {
        final Object payload = snapshot.getPayload();
        if (payload != null) {
            final CreateSnapshotPayload createSnapshotPayload = (CreateSnapshotPayload) payload;
            if (createSnapshotPayload.getQuiescevm()) {
                throw new InvalidParameterValueException("can't handle quiescevm equal true for volume snapshot");
            }
        }

        final SnapshotVO snapshotVO = snapshotDao.acquireInLockTable(snapshot.getId());
        if (snapshotVO == null) {
            throw new CloudRuntimeException("Failed to get lock on snapshot:" + snapshot.getId());
        }

        try {
            final VolumeInfo volumeInfo = snapshot.getBaseVolume();
            volumeInfo.stateTransit(Volume.Event.SnapshotRequested);
            SnapshotResult result = null;
            try {
                result = snapshotSvr.takeSnapshot(snapshot);
                if (result.isFailed()) {
                    s_logger.debug("Failed to take snapshot: " + result.getResult());
                    throw new CloudRuntimeException(result.getResult());
                }
            } finally {
                if (result != null && result.isSuccess()) {
                    volumeInfo.stateTransit(Volume.Event.OperationSucceeded);
                } else {
                    volumeInfo.stateTransit(Volume.Event.OperationFailed);
                }
            }

            snapshot = result.getSnashot();
            final DataStore primaryStore = snapshot.getDataStore();
            final boolean backupFlag = Boolean.parseBoolean(configDao.getValue(Config.BackupSnapshotAfterTakingSnapshot.toString()));

            final SnapshotInfo backupedSnapshot;
            if (backupFlag) {
                backupedSnapshot = backupSnapshot(snapshot);
            } else {
                // Fake it to get the transitions to fire in the proper order
                s_logger.debug("skipping backup of snapshot due to configuration " + Config.BackupSnapshotAfterTakingSnapshot.toString());
                final SnapshotObject snapObj = (SnapshotObject) snapshot;

                try {
                    snapObj.processEvent(Snapshot.Event.OperationNotPerformed);
                } catch (final NoTransitionException e) {
                    s_logger.debug("Failed to change state: " + snapshot.getId() + ": " + e.toString());
                    throw new CloudRuntimeException(e.toString());
                }
                backupedSnapshot = snapshot;
            }

            try {
                final SnapshotInfo parent = snapshot.getParent();
                if (backupedSnapshot != null && parent != null) {
                    Long parentSnapshotId = parent.getId();
                    while (parentSnapshotId != null && parentSnapshotId != 0L) {
                        final SnapshotDataStoreVO snapshotDataStoreVO = snapshotStoreDao.findByStoreSnapshot(primaryStore.getRole(), primaryStore.getId(), parentSnapshotId);
                        if (snapshotDataStoreVO != null) {
                            parentSnapshotId = snapshotDataStoreVO.getParentSnapshotId();
                            snapshotStoreDao.remove(snapshotDataStoreVO.getId());
                        } else {
                            parentSnapshotId = null;
                        }
                    }
                    final SnapshotDataStoreVO snapshotDataStoreVO = snapshotStoreDao.findByStoreSnapshot(primaryStore.getRole(), primaryStore.getId(), snapshot.getId());
                    if (snapshotDataStoreVO != null) {
                        snapshotDataStoreVO.setParentSnapshotId(0L);
                        snapshotStoreDao.update(snapshotDataStoreVO.getId(), snapshotDataStoreVO);
                    }
                }
            } catch (final Exception e) {
                s_logger.debug("Failed to clean up snapshots on primary storage", e);
            }
            return backupedSnapshot;
        } finally {
            if (snapshotVO != null) {
                snapshotDao.releaseFromLockTable(snapshot.getId());
            }
        }
    }

    @Override
    public SnapshotInfo backupSnapshot(final SnapshotInfo snapshot) {
        final SnapshotInfo parentSnapshot = snapshot.getParent();

        if (parentSnapshot != null && snapshot.getPath().equalsIgnoreCase(parentSnapshot.getPath())) {
            s_logger.debug("backup an empty snapshot");
            // don't need to backup this snapshot
            final SnapshotDataStoreVO parentSnapshotOnBackupStore = snapshotStoreDao.findBySnapshot(parentSnapshot.getId(), DataStoreRole.Image);
            if (parentSnapshotOnBackupStore != null && parentSnapshotOnBackupStore.getState() == State.Ready) {
                final DataStore store = dataStoreMgr.getDataStore(parentSnapshotOnBackupStore.getDataStoreId(), parentSnapshotOnBackupStore.getRole());

                final SnapshotInfo snapshotOnImageStore = (SnapshotInfo) store.create(snapshot);
                snapshotOnImageStore.processEvent(Event.CreateOnlyRequested);

                final SnapshotObjectTO snapTO = new SnapshotObjectTO();
                snapTO.setPath(parentSnapshotOnBackupStore.getInstallPath());

                final CreateObjectAnswer createSnapshotAnswer = new CreateObjectAnswer(snapTO);

                snapshotOnImageStore.processEvent(Event.OperationSuccessed, createSnapshotAnswer);
                final SnapshotObject snapObj = (SnapshotObject) snapshot;
                try {
                    snapObj.processEvent(Snapshot.Event.OperationNotPerformed);
                } catch (final NoTransitionException e) {
                    s_logger.debug("Failed to change state: " + snapshot.getId() + ": " + e.toString());
                    throw new CloudRuntimeException(e.toString());
                }
                return snapshotDataFactory.getSnapshot(snapObj.getId(), store);
            } else {
                s_logger.debug("parent snapshot hasn't been backed up yet");
            }
        }

        // determine full snapshot backup or not

        boolean fullBackup = true;
        SnapshotDataStoreVO parentSnapshotOnBackupStore = snapshotStoreDao.findLatestSnapshotForVolume(snapshot.getVolumeId(), DataStoreRole.Image);
        final SnapshotDataStoreVO parentSnapshotOnPrimaryStore = snapshotStoreDao.findLatestSnapshotForVolume(snapshot.getVolumeId(), DataStoreRole.Primary);
        final HypervisorType hypervisorType = snapshot.getBaseVolume().getHypervisorType();
        if (parentSnapshotOnPrimaryStore != null && parentSnapshotOnBackupStore != null && hypervisorType == Hypervisor.HypervisorType.XenServer) { // CS does incremental backup
            // only for XenServer

            // In case of volume migration from one pool to other pool, CS should take full snapshot to avoid any issues with delta chain,
            // to check if this is a migrated volume, compare the current pool id of volume and store_id of oldest snapshot on primary for this volume.
            // Why oldest? Because at this point CS has two snapshot on primary entries for same volume, one with old pool_id and other one with
            // current pool id. So, verify and if volume found to be migrated, delete snapshot entry with previous pool store_id.
            final SnapshotDataStoreVO oldestSnapshotOnPrimary = snapshotStoreDao.findOldestSnapshotForVolume(snapshot.getVolumeId(), DataStoreRole.Primary);
            final VolumeVO volume = volumeDao.findById(snapshot.getVolumeId());
            if (oldestSnapshotOnPrimary != null) {
                if (oldestSnapshotOnPrimary.getDataStoreId() == volume.getPoolId()) {
                    final int _deltaSnapshotMax = NumbersUtil.parseInt(configDao.getValue("snapshot.delta.max"),
                            SnapshotManager.DELTAMAX);
                    final int deltaSnap = _deltaSnapshotMax;
                    int i;

                    for (i = 1; i < deltaSnap; i++) {
                        final Long prevBackupId = parentSnapshotOnBackupStore.getParentSnapshotId();
                        if (prevBackupId == 0) {
                            break;
                        }
                        parentSnapshotOnBackupStore = snapshotStoreDao.findBySnapshot(prevBackupId, DataStoreRole.Image);
                        if (parentSnapshotOnBackupStore == null) {
                            break;
                        }
                    }

                    if (i >= deltaSnap) {
                        fullBackup = true;
                    } else {
                        fullBackup = false;
                    }
                } else {
                    // if there is an snapshot entry for previousPool(primary storage) of migrated volume, delete it becasue CS created one more snapshot entry for current pool
                    snapshotStoreDao.remove(oldestSnapshotOnPrimary.getId());
                }
            }
        }

        snapshot.addPayload(fullBackup);
        return snapshotSvr.backupSnapshot(snapshot);
    }

    @Override
    public boolean revertSnapshot(final SnapshotInfo snapshot) {
        if (canHandle(snapshot, SnapshotOperation.REVERT) == StrategyPriority.CANT_HANDLE) {
            throw new UnsupportedOperationException("Reverting not supported. Create a template or volume based on the snapshot instead.");
        }

        final SnapshotVO snapshotVO = snapshotDao.acquireInLockTable(snapshot.getId());

        if (snapshotVO == null) {
            throw new CloudRuntimeException("Failed to get lock on snapshot:" + snapshot.getId());
        }

        try {
            final VolumeInfo volumeInfo = snapshot.getBaseVolume();
            final StoragePool store = (StoragePool) volumeInfo.getDataStore();

            if (store != null && store.getStatus() != StoragePoolStatus.Up) {
                snapshot.processEvent(Event.OperationFailed);

                throw new CloudRuntimeException("store is not in up state");
            }

            volumeInfo.stateTransit(Volume.Event.RevertSnapshotRequested);

            boolean result = false;

            try {
                result = snapshotSvr.revertSnapshot(snapshot);

                if (!result) {
                    s_logger.debug("Failed to revert snapshot: " + snapshot.getId());

                    throw new CloudRuntimeException("Failed to revert snapshot: " + snapshot.getId());
                }
            } finally {
                if (result) {
                    volumeInfo.stateTransit(Volume.Event.OperationSucceeded);
                } else {
                    volumeInfo.stateTransit(Volume.Event.OperationFailed);
                }
            }

            return result;
        } finally {
            if (snapshotVO != null) {
                snapshotDao.releaseFromLockTable(snapshot.getId());
            }
        }
    }
}
