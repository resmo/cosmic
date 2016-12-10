package com.cloud.storage.snapshot;

import com.cloud.api.command.user.snapshot.CreateSnapshotPolicyCmd;
import com.cloud.api.command.user.snapshot.DeleteSnapshotPoliciesCmd;
import com.cloud.api.command.user.snapshot.ListSnapshotPoliciesCmd;
import com.cloud.api.command.user.snapshot.ListSnapshotsCmd;
import com.cloud.api.command.user.snapshot.UpdateSnapshotPolicyCmd;
import com.cloud.api.commands.ListRecurringSnapshotScheduleCmd;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.storage.Snapshot;
import com.cloud.storage.Volume;
import com.cloud.user.Account;
import com.cloud.utils.Pair;

import java.util.List;

public interface SnapshotApiService {

    /**
     * List all snapshots of a disk volume. Optionally lists snapshots created by specified interval
     *
     * @param cmd the command containing the search criteria (order by, limit, etc.)
     * @return list of snapshots
     */
    Pair<List<? extends Snapshot>, Integer> listSnapshots(ListSnapshotsCmd cmd);

    /**
     * Delete specified snapshot from the specified. If no other policies are assigned it calls destroy snapshot. This
     * will be
     * used for manual snapshots too.
     *
     * @param snapshotId TODO
     */
    boolean deleteSnapshot(long snapshotId);

    /**
     * Creates a policy with specified schedule. maxSnaps specifies the number of most recent snapshots that are to be
     * retained.
     * If the number of snapshots go beyond maxSnaps the oldest snapshot is deleted
     *
     * @param cmd         the command that
     * @param policyOwner TODO
     * @return the newly created snapshot policy if success, null otherwise
     */
    SnapshotPolicy createPolicy(CreateSnapshotPolicyCmd cmd, Account policyOwner);

    /**
     * Get the recurring snapshots scheduled for this volume currently along with the time at which they are scheduled
     *
     * @param cmd the command wrapping the volumeId (volume for which the snapshots are required) and policyId (to show
     *            snapshots for only this policy).
     * @return The list of snapshot schedules.
     */
    public List<? extends SnapshotSchedule> findRecurringSnapshotSchedule(ListRecurringSnapshotScheduleCmd cmd);

    /**
     * list all snapshot policies assigned to the specified volume
     *
     * @param cmd the command that specifies the volume criteria
     * @return list of snapshot policies
     */
    Pair<List<? extends SnapshotPolicy>, Integer> listPoliciesforVolume(ListSnapshotPoliciesCmd cmd);

    boolean deleteSnapshotPolicies(DeleteSnapshotPoliciesCmd cmd);

    Snapshot allocSnapshot(Long volumeId, Long policyId, String snapshotName) throws ResourceAllocationException;

    /**
     * Create a snapshot of a volume
     *
     * @param snapshotOwner TODO
     * @param cmd           the API command wrapping the parameters for creating the snapshot (mainly volumeId)
     * @return the Snapshot that was created
     */
    Snapshot createSnapshot(Long volumeId, Long policyId, Long snapshotId, Account snapshotOwner);

    /**
     * @param vol
     * @return
     */
    Long getHostIdForSnapshotOperation(Volume vol);

    Snapshot revertSnapshot(Long snapshotId);

    SnapshotPolicy updateSnapshotPolicy(UpdateSnapshotPolicyCmd updateSnapshotPolicyCmd);
}
