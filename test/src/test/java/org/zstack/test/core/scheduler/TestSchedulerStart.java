package org.zstack.test.core.scheduler;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.scheduler.SchedulerFacade;
import org.zstack.core.scheduler.SchedulerVO;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.simulator.kvm.VolumeSnapshotKvmSimulator;
import org.zstack.test.*;
import org.zstack.test.core.gc.TestGC9PreCase;
import org.zstack.test.deployer.Deployer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by root on 8/1/16.
 */
public class TestSchedulerStart {
    ComponentLoader loader;
    Api api;
    @Autowired
    SchedulerFacade scheduler;
    DatabaseFacade dbf;
    CloudBus bus;
    Deployer deployer;
    SessionInventory session;
    VolumeSnapshotKvmSimulator snapshotKvmSimulator;

    @Before
    public void setUp() throws Exception {
        UnitTestUtils.runTestCase(TestSchedulerCron.class);
        WebBeanConstructor con = new WebBeanConstructor();
        deployer = new Deployer("deployerXml/kvm/TestCreateVmOnKvm.xml", con);
        deployer.addSpringConfig("KVMRelated.xml");
        deployer.addSpringConfig("SchedulerFacade.xml");
        deployer.build();
        api = deployer.getApi();
        loader = deployer.getComponentLoader();
        bus = loader.getComponent(CloudBus.class);
        dbf = loader.getComponent(DatabaseFacade.class);
        scheduler = loader.getComponent(SchedulerFacade.class);
        snapshotKvmSimulator = loader.getComponent(VolumeSnapshotKvmSimulator.class);
        session = api.loginAsAdmin();
    }

    @Test
    public void test() throws InterruptedException, ApiSenderException, SchedulerException {
        Assert.assertNotNull(scheduler);
        TimeUnit.SECONDS.sleep(5);
        List<SchedulerVO> vos = dbf.listAll(SchedulerVO.class);
        long record = dbf.count(VolumeSnapshotVO.class);
        Assert.assertEquals(6,record);

    }
}
