package org.zstack.test.mevoco;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.allocator.HostCapacityVO;
import org.zstack.header.configuration.InstanceOfferingInventory;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HostVO;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.network.l2.L2NetworkInventory;
import org.zstack.header.network.l3.L3NetworkInventory;
import org.zstack.header.network.l3.UsedIpVO;
import org.zstack.header.storage.primary.PrimaryStorageCapacityVO;
import org.zstack.header.storage.primary.PrimaryStorageInventory;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.kvm.KVMAgentCommands.StartVmCmd;
import org.zstack.kvm.KVMSystemTags;
import org.zstack.mevoco.KVMAddOns.NicQos;
import org.zstack.mevoco.KVMAddOns.VolumeQos;
import org.zstack.mevoco.MevocoConstants;
import org.zstack.mevoco.MevocoGlobalConfig;
import org.zstack.mevoco.MevocoSystemTags;
import org.zstack.network.service.flat.FlatDhcpBackend.ApplyDhcpCmd;
import org.zstack.network.service.flat.FlatDhcpBackend.DhcpInfo;
import org.zstack.network.service.flat.FlatDhcpBackend.PrepareDhcpCmd;
import org.zstack.network.service.flat.FlatNetworkServiceSimulatorConfig;
import org.zstack.network.service.flat.FlatNetworkSystemTags;
import org.zstack.simulator.kvm.KVMSimulatorConfig;
import org.zstack.storage.primary.local.APIGetLocalStorageHostDiskCapacityReply;
import org.zstack.storage.primary.local.APIGetLocalStorageHostDiskCapacityReply.HostDiskCapacity;
import org.zstack.storage.primary.local.LocalStorageHostRefVO;
import org.zstack.storage.primary.local.LocalStorageSimulatorConfig;
import org.zstack.storage.primary.local.LocalStorageSimulatorConfig.Capacity;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.DBUtil;
import org.zstack.test.WebBeanConstructor;
import org.zstack.test.deployer.Deployer;
import org.zstack.utils.Utils;
import org.zstack.utils.data.SizeUnit;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.zstack.core.Platform._;

/**
 * 1. create 2 vms
 * 2. delete a vm
 *
 * confirm the memory capacity and disk capacity returned correctly
 *
 */
public class TestMevoco5 {
    CLogger logger = Utils.getLogger(TestMevoco5.class);
    Deployer deployer;
    Api api;
    ComponentLoader loader;
    CloudBus bus;
    DatabaseFacade dbf;
    SessionInventory session;
    LocalStorageSimulatorConfig config;
    FlatNetworkServiceSimulatorConfig fconfig;
    KVMSimulatorConfig kconfig;
    long totalSize = SizeUnit.GIGABYTE.toByte(100);

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        WebBeanConstructor con = new WebBeanConstructor();
        deployer = new Deployer("deployerXml/mevoco/TestMevoco5.xml", con);
        deployer.addSpringConfig("KVMRelated.xml");
        deployer.addSpringConfig("mevoco.xml");
        deployer.addSpringConfig("localStorage.xml");
        deployer.addSpringConfig("localStorageSimulator.xml");
        deployer.addSpringConfig("flatNetworkServiceSimulator.xml");
        deployer.load();

        loader = deployer.getComponentLoader();
        bus = loader.getComponent(CloudBus.class);
        dbf = loader.getComponent(DatabaseFacade.class);
        config = loader.getComponent(LocalStorageSimulatorConfig.class);
        fconfig = loader.getComponent(FlatNetworkServiceSimulatorConfig.class);
        kconfig = loader.getComponent(KVMSimulatorConfig.class);

        Capacity c = new Capacity();
        c.total = totalSize;
        c.avail = totalSize;

        config.capacityMap.put("host1", c);

        deployer.build();
        api = deployer.getApi();
        session = api.loginAsAdmin();
    }
    
	@Test
	public void test() throws ApiSenderException, InterruptedException {
        long totalMemorySize = SizeUnit.GIGABYTE.toByte(8);
        VmInstanceInventory vm = deployer.vms.get("TestVm");
        VmInstanceInventory vm1 = deployer.vms.get("TestVm1");
        HostInventory host = deployer.hosts.get("host1");
        PrimaryStorageInventory ps = deployer.primaryStorages.get("local");

        api.stopVmInstance(vm.getUuid());
        HostCapacityVO hcap = dbf.findByUuid(host.getUuid(), HostCapacityVO.class);

        long availMem = totalMemorySize - (long)(vm1.getMemorySize() / MevocoGlobalConfig.MEMORY_OVER_PROVISIONING_RATIO.value(Float.class));
        Assert.assertEquals(availMem, hcap.getAvailableMemory());

        api.destroyVmInstance(vm.getUuid());
        PrimaryStorageCapacityVO pscap = dbf.findByUuid(ps.getUuid(), PrimaryStorageCapacityVO.class);
        long availSize = totalSize - Math.round(vm1.getRootVolume().getSize() / MevocoGlobalConfig.PRIMARY_STORAGE_OVER_PROVISIONING_RATIO.value(Double.class));
        Assert.assertEquals(availSize, pscap.getAvailableCapacity());

        MevocoGlobalConfig.MEMORY_OVER_PROVISIONING_RATIO.updateValue(1);
        TimeUnit.SECONDS.sleep(2);
        hcap = dbf.findByUuid(host.getUuid(), HostCapacityVO.class);
        availMem = totalMemorySize - (long)(vm1.getMemorySize() / MevocoGlobalConfig.MEMORY_OVER_PROVISIONING_RATIO.value(Float.class));
        Assert.assertEquals(availMem, hcap.getAvailableMemory());

        MevocoGlobalConfig.PRIMARY_STORAGE_OVER_PROVISIONING_RATIO.updateValue(1);
        TimeUnit.SECONDS.sleep(2);
        pscap = dbf.findByUuid(ps.getUuid(), PrimaryStorageCapacityVO.class);
        availSize = totalSize - Math.round(vm1.getRootVolume().getSize() / MevocoGlobalConfig.PRIMARY_STORAGE_OVER_PROVISIONING_RATIO.value(Double.class));
        Assert.assertEquals(availSize, pscap.getAvailableCapacity());

        LocalStorageHostRefVO lref = dbf.findByUuid(host.getUuid(), LocalStorageHostRefVO.class);
        availSize = totalSize - Math.round(vm1.getRootVolume().getSize() / MevocoGlobalConfig.PRIMARY_STORAGE_OVER_PROVISIONING_RATIO.value(Double.class));
        Assert.assertEquals(availSize, lref.getAvailableCapacity());

        MevocoGlobalConfig.MEMORY_OVER_PROVISIONING_RATIO.updateValue(2);
        TimeUnit.SECONDS.sleep(2);
        hcap = dbf.findByUuid(host.getUuid(), HostCapacityVO.class);
        availMem = totalMemorySize - (long)(vm1.getMemorySize() / MevocoGlobalConfig.MEMORY_OVER_PROVISIONING_RATIO.value(Float.class));
        Assert.assertEquals(availMem, hcap.getAvailableMemory());

        api.destroyVmInstance(vm1.getUuid());
        pscap = dbf.findByUuid(ps.getUuid(), PrimaryStorageCapacityVO.class);
        Assert.assertEquals(pscap.getTotalCapacity(), pscap.getAvailableCapacity());

        lref = dbf.findByUuid(host.getUuid(), LocalStorageHostRefVO.class);
        Assert.assertEquals(lref.getTotalCapacity(), lref.getAvailableCapacity());

        hcap = dbf.findByUuid(host.getUuid(), HostCapacityVO.class);
        Assert.assertEquals(hcap.getTotalMemory(), hcap.getAvailableMemory());

        APIGetLocalStorageHostDiskCapacityReply reply = api.getLocalStorageHostCapacity(ps.getUuid(), host.getUuid());
        HostDiskCapacity c = reply.getInventories().get(0);

        Assert.assertEquals(lref.getTotalCapacity(), c.getTotalCapacity());
        Assert.assertEquals(lref.getAvailableCapacity(), c.getAvailableCapacity());
        Assert.assertEquals(lref.getTotalPhysicalCapacity(), c.getTotalPhysicalCapacity());
        Assert.assertEquals(lref.getAvailablePhysicalCapacity(), c.getAvailablePhysicalCapacity());

        reply = api.getLocalStorageHostCapacity(ps.getUuid(), null);
        c = reply.getInventories().get(0);

        Assert.assertEquals(lref.getTotalCapacity(), c.getTotalCapacity());
        Assert.assertEquals(lref.getAvailableCapacity(), c.getAvailableCapacity());
        Assert.assertEquals(lref.getTotalPhysicalCapacity(), c.getTotalPhysicalCapacity());
        Assert.assertEquals(lref.getAvailablePhysicalCapacity(), c.getAvailablePhysicalCapacity());
    }
}