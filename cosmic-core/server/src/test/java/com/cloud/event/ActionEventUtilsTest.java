package com.cloud.event;

import com.cloud.configuration.Config;
import com.cloud.context.CallContext;
import com.cloud.dao.EntityManager;
import com.cloud.event.dao.EventDao;
import com.cloud.framework.config.dao.ConfigurationDao;
import com.cloud.framework.events.Event;
import com.cloud.framework.events.EventBus;
import com.cloud.network.IpAddress;
import com.cloud.projects.dao.ProjectDao;
import com.cloud.user.AccountVO;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.component.ComponentContext;
import com.cloud.vm.VirtualMachine;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ComponentContext.class)
public class ActionEventUtilsTest {
    //Predictable constants used throughout this test.
    public static final long EVENT_ID = 1;
    public static final long USER_ID = 1;
    public static final long ACCOUNT_ID = 1;

    //Keep track of the static field values between tests.
    //A horrid abuse of reflection required due to the strange
    //static/inject pattern found in ActionEventUtils.
    protected Map<String, Object> staticFieldValues = new HashMap<>();

    //List of events published on the event bus. Handled via a mocked method.
    //Cleared on every run.
    protected List<Event> publishedEvents = new ArrayList<>();

    //Mock fields. These are injected into ActionEventUtils by the setup() method.
    @Mock
    protected EventDao eventDao;

    @Mock
    protected AccountDao accountDao;

    @Mock
    protected UserDao userDao;

    @Mock
    protected ProjectDao projectDao;

    @Mock
    protected EntityManager entityMgr;

    @Mock
    protected ConfigurationDao configDao;

    @Mock
    protected EventBus eventBus;

    /**
     * This setup method injects the mocked beans into the ActionEventUtils class.
     * Because ActionEventUtils has static methods, we must also remember these fields
     * and restore them later, as otherwise strange behavior can result in other unit
     * tests due to the way the JVM handles static fields.
     *
     * @throws Exception
     */
    @Before
    public void setup() throws Exception {
        publishedEvents = new ArrayList<>();
        staticFieldValues = new HashMap<>();
        setupCommonMocks();

        final ActionEventUtils utils = new ActionEventUtils();

        for (final Field field : ActionEventUtils.class.getDeclaredFields()) {
            if (field.getAnnotation(Inject.class) != null) {
                field.setAccessible(true);

                try {
                    //Inject the mocked field from this class into the ActionEventUtils
                    //and keep track of its original value.
                    final Field mockField = this.getClass().getDeclaredField(field.getName());
                    field.set(utils, mockField.get(this));
                    final Field staticField = ActionEventUtils.class.getDeclaredField("s_" + field.getName());
                    staticFieldValues.put(field.getName(), staticField.get(null));
                } catch (final Exception e) {
                    // ignore missing fields
                }
            }
        }

        utils.init();
    }

    /**
     * Set up the common specialized mocks that are needed to make the ActionEventUtils class behave in a
     * predictable way. This method only mocks things that are common to all the tests. Each individual test
     * also mocks some other methods (e.g. find user/account) by itself.
     */
    public void setupCommonMocks() throws Exception {
        //Some basic mocks.
        Mockito.when(configDao.getValue(Config.PublishActionEvent.key())).thenReturn("true");
        PowerMockito.mockStatic(ComponentContext.class);
        Mockito.when(ComponentContext.getComponent(EventBus.class)).thenReturn(eventBus);

        //Needed for persist to actually set an ID that can be returned from the ActionEventUtils
        //methods.
        Mockito.when(eventDao.persist(Mockito.any(EventVO.class))).thenAnswer(new Answer<EventVO>() {
            @Override
            public EventVO answer(final InvocationOnMock invocation) throws Throwable {
                final EventVO event = (EventVO) invocation.getArguments()[0];
                final Field id = event.getClass().getDeclaredField("id");
                id.setAccessible(true);
                id.set(event, EVENT_ID);
                return event;
            }
        });

        //Needed to record events published on the bus.
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) throws Throwable {
                final Event event = (Event) invocation.getArguments()[0];
                publishedEvents.add(event);
                return null;
            }
        }).when(eventBus).publish(Mockito.any(Event.class));
    }

    /**
     * This teardown method restores the ActionEventUtils static field values to their original values,
     * keeping the mocked mess inside this class.
     */
    @After
    public void teardown() {
        final ActionEventUtils utils = new ActionEventUtils();

        for (final String fieldName : staticFieldValues.keySet()) {
            try {
                final Field field = ActionEventUtils.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(utils, staticFieldValues.get(fieldName));
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }

        utils.init();
    }

    @Test
    public void testPopulateFirstClassEntities() {
        final AccountVO account = new AccountVO("testaccount", 1L, "networkdomain", (short) 0, "uuid");
        account.setId(ACCOUNT_ID);
        final UserVO user = new UserVO(1, "testuser", "password", "firstname", "lastName", "email", "timezone",
                UUID.randomUUID().toString(), User.Source.UNKNOWN);

        Mockito.when(accountDao.findById(ACCOUNT_ID)).thenReturn(account);
        Mockito.when(userDao.findById(USER_ID)).thenReturn(user);

        CallContext.register(user, account);

        //Inject some entity UUIDs into the call context
        final String instanceUuid = UUID.randomUUID().toString();
        final String ipUuid = UUID.randomUUID().toString();
        CallContext.current().putContextParameter(VirtualMachine.class, instanceUuid);
        CallContext.current().putContextParameter(IpAddress.class, ipUuid);

        ActionEventUtils.onActionEvent(USER_ID, ACCOUNT_ID, account.getDomainId(), "StaticNat", "Test event");

        //Assertions
        Assert.assertNotEquals(publishedEvents.size(), 0);
        Assert.assertEquals(publishedEvents.size(), 1);

        final Event event = publishedEvents.get(0);
        Assert.assertNotNull(event.getDescription());

        final JsonObject json = new JsonParser().parse(event.getDescription()).getAsJsonObject();

        Assert.assertTrue(json.has("VirtualMachine"));
        Assert.assertTrue(json.has("IpAddress"));
        Assert.assertEquals(json.get("VirtualMachine").getAsString(), instanceUuid);
        Assert.assertEquals(json.get("IpAddress").getAsString(), ipUuid);

        CallContext.unregister();
    }
}
