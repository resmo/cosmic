package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.storage.MigrateVolumeCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.hypervisor.kvm.resource.xml.LibvirtDiskDef;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

import java.util.List;

import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;
import org.libvirt.parameters.DomainBlockCopyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ResourceWrapper(handles = MigrateVolumeCommand.class)
public final class LibvirtMigrateVolumeCommandWrapper extends CommandWrapper<MigrateVolumeCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = LoggerFactory.getLogger(LibvirtMigrateVolumeCommandWrapper.class);

    @Override
    public Answer execute(final MigrateVolumeCommand command, final LibvirtComputingResource libvirtComputingResource) {
        String result = null;

        final String vmName = command.getAttachedVmName();

        LibvirtDiskDef disk = null;
        List<LibvirtDiskDef> disks;

        Domain dm = null;
        Connect conn;

        String currentVolumePath;

        try {
            final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();

            conn = libvirtUtilitiesHelper.getConnectionByVmName(vmName);
            disks = libvirtComputingResource.getDisks(conn, vmName);
            dm = conn.domainLookupByName(vmName);

            for (LibvirtDiskDef diskDef : disks) {
                if (diskDef.getDiskPath().contains(command.getVolumePath())) {
                    disk = diskDef;
                    break;
                }
            }

            if (disk != null) {
                currentVolumePath = disk.getDiskPath();

                disk.setDiskPath("/mnt/" + command.getPool().getUuid() + "/" + command.getVolumePath());

                dm.blockCopy(currentVolumePath, disk.toString(), new DomainBlockCopyParameters(), 0, true);
            } else {
                throw new LibvirtException("Couldn't find disk: " + command.getVolumePath() + " on vm: " + dm.getName());
            }
        } catch (final LibvirtException e) {
            s_logger.debug("Can't migrate disk: " + e.getMessage());
            result = e.getMessage();
        } finally {
            try {
                if (dm != null) {
                    dm.free();
                }
            } catch (final LibvirtException e) {
                s_logger.trace("Ignoring libvirt error.", e);
            }
        }

        return new Answer(command, result == null, result);
    }
}
