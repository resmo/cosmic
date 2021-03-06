package com.cloud.context;

import com.cloud.dao.EntityManager;
import com.cloud.exception.CloudAuthenticationException;
import com.cloud.managed.threadlocal.ManagedThreadLocal;
import com.cloud.projects.Project;
import com.cloud.user.Account;
import com.cloud.user.User;
import com.cloud.utils.UuidUtils;
import com.cloud.utils.exception.CloudRuntimeException;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * CallContext records information about the environment the call is made.  This
 * class must be always be available in all CloudStack code.  Every thread
 * entry point must set the context and remove it when the thread finishes.
 */
public class CallContext {
    private static final Logger s_logger = LoggerFactory.getLogger(CallContext.class);
    private static final ManagedThreadLocal<CallContext> s_currentContext = new ManagedThreadLocal<>();
    private static final ManagedThreadLocal<Stack<CallContext>> s_currentContextStack = new ManagedThreadLocal<Stack<CallContext>>() {
        @Override
        protected Stack<CallContext> initialValue() {
            return new Stack<>();
        }
    };
    static EntityManager s_entityMgr;
    private final Map<Object, Object> context = new HashMap<>();
    private String contextId;
    private Account account;
    private long accountId;
    private long startEventId = 0;
    private String eventDescription;
    private String eventDetails;
    private String eventType;
    private boolean isEventDisplayEnabled = true; // default to true unless specifically set
    private User user;
    private long userId;
    private Project project;

    protected CallContext() {
    }

    protected CallContext(final long userId, final long accountId, final String contextId) {
        this.userId = userId;
        this.accountId = accountId;
        this.contextId = contextId;
    }

    protected CallContext(final User user, final Account account, final String contextId) {
        this.user = user;
        userId = user.getId();
        this.account = account;
        accountId = account.getId();
        this.contextId = contextId;
    }

    public static void init(final EntityManager entityMgr) {
        s_entityMgr = entityMgr;
    }

    public static CallContext registerPlaceHolderContext() {
        final CallContext context = new CallContext(0, 0, UUID.randomUUID().toString());
        s_currentContext.set(context);

        s_currentContextStack.get().push(context);
        return context;
    }

    public static CallContext register(final String callingUserUuid, final String callingAccountUuid) {
        final Account account = s_entityMgr.findByUuid(Account.class, callingAccountUuid);
        if (account == null) {
            throw new CloudAuthenticationException("The account is no longer current.").add(Account.class, callingAccountUuid);
        }

        final User user = s_entityMgr.findByUuid(User.class, callingUserUuid);
        if (user == null) {
            throw new CloudAuthenticationException("The user is no longer current.").add(User.class, callingUserUuid);
        }
        return register(user, account);
    }

    public static CallContext register(final User callingUser, final Account callingAccount) {
        return register(callingUser, callingAccount, UUID.randomUUID().toString());
    }

    /**
     * This method should only be called if you can propagate the context id
     * from another CallContext.
     *
     * @param callingUser    calling user
     * @param callingAccount calling account
     * @param contextId      context id propagated from another call context
     * @return CallContext
     */
    public static CallContext register(final User callingUser, final Account callingAccount, final String contextId) {
        return register(callingUser, callingAccount, null, null, contextId);
    }

    protected static CallContext register(final User callingUser, final Account callingAccount, final Long userId, final Long accountId, final String contextId) {
        /*
                Unit tests will have multiple times of setup/tear-down call to this, remove assertions to all unit test to run
                assert s_currentContext.get() == null : "There's a context already so what does this new register context mean? " + s_currentContext.get().toString();
                if (s_currentContext.get() != null) { // FIXME: This should be removed soon.  I added this check only to surface all the places that have this problem.
                    throw new CloudRuntimeException("There's a context already so what does this new register context mean? " + s_currentContext.get().toString());
                }
        */
        CallContext callingContext = null;
        if (userId == null || accountId == null) {
            callingContext = new CallContext(callingUser, callingAccount, contextId);
        } else {
            callingContext = new CallContext(userId, accountId, contextId);
        }
        s_currentContext.set(callingContext);
        MDC.put("ctx", " (ctx: " + UuidUtils.first(contextId) + ")");
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Registered: " + callingContext);
        }

        s_currentContextStack.get().push(callingContext);

        return callingContext;
    }

    public static CallContext register(final long callingUserId, final long callingAccountId) throws CloudAuthenticationException {
        final Account account = s_entityMgr.findById(Account.class, callingAccountId);
        if (account == null) {
            throw new CloudAuthenticationException("The account is no longer current.").add(Account.class, Long.toString(callingAccountId));
        }
        final User user = s_entityMgr.findById(User.class, callingUserId);
        if (user == null) {
            throw new CloudAuthenticationException("The user is no longer current.").add(User.class, Long.toString(callingUserId));
        }
        return register(user, account);
    }

    public static CallContext register(final long callingUserId, final long callingAccountId, final String contextId) throws CloudAuthenticationException {
        final Account account = s_entityMgr.findById(Account.class, callingAccountId);
        if (account == null) {
            throw new CloudAuthenticationException("The account is no longer current.").add(Account.class, Long.toString(callingAccountId));
        }
        final User user = s_entityMgr.findById(User.class, callingUserId);
        if (user == null) {
            throw new CloudAuthenticationException("The user is no longer current.").add(User.class, Long.toString(callingUserId));
        }
        return register(user, account, contextId);
    }

    public static void unregisterAll() {
        while (unregister() != null) {
            // NOOP
        }
    }

    public static CallContext unregister() {
        final CallContext context = s_currentContext.get();
        if (context == null) {
            return null;
        }
        s_currentContext.remove();
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Unregistered: " + context);
        }
        MDC.remove("ctx");

        final Stack<CallContext> stack = s_currentContextStack.get();
        stack.pop();

        if (!stack.isEmpty()) {
            s_currentContext.set(stack.peek());
        } else {
            s_currentContext.set(null);
        }

        return context;
    }

    public String getContextId() {
        return contextId;
    }

    public static void setActionEventInfo(final String eventType, final String description) {
        final CallContext context = CallContext.current();
        if (context != null) {
            context.setEventType(eventType);
            context.setEventDescription(description);
        }
    }

    public static CallContext current() {
        CallContext context = s_currentContext.get();

        // TODO other than async job and api dispatches, there are many system background running threads
        // that do not setup CallContext at all, however, many places in code that are touched by these background tasks
        // assume not-null CallContext. Following is a fix to address therefore caused NPE problems
        //
        // There are security implications with this. It assumes that all system background running threads are
        // indeed have no problem in running under system context.
        //
        if (context == null) {
            context = registerSystemCallContextOnceOnly();
        }

        return context;
    }

    public static CallContext registerSystemCallContextOnceOnly() {
        try {
            final CallContext context = s_currentContext.get();
            if (context == null) {
                return register(null, null, User.UID_SYSTEM, Account.ACCOUNT_ID_SYSTEM, UUID.randomUUID().toString());
            }
            assert context.getCallingUserId() == User.UID_SYSTEM : "You are calling a very specific method that registers a one time system context.  This method is meant for " +
                    "background threads that does processing.";
            return context;
        } catch (final Exception e) {
            s_logger.error("Failed to register the system call context.", e);
            throw new CloudRuntimeException("Failed to register system call context", e);
        }
    }

    public long getCallingUserId() {
        return userId;
    }

    /**
     * @param key any not null key object
     * @return the value of the key from context map
     * @throws NullPointerException if the specified key is nul
     */
    public Object getContextParameter(final Object key) {
        Object value = context.get(key);
        //check if the value is present in the toString value of the key
        //due to a bug in the way we update the key by serializing and deserializing, it sometimes gets toString value of the key. @see com.cloud.api.ApiAsyncJobDispatcher#runJob
        if (value == null) {
            value = context.get(key.toString());
        }
        return value;
    }

    public long getStartEventId() {
        return startEventId;
    }

    public void setStartEventId(final long startEventId) {
        this.startEventId = startEventId;
    }

    public String getCallingAccountUuid() {
        return getCallingAccount().getUuid();
    }

    public Account getCallingAccount() {
        if (account == null) {
            account = s_entityMgr.findById(Account.class, accountId);
        }
        return account;
    }

    public String getCallingUserUuid() {
        return getCallingUser().getUuid();
    }

    public User getCallingUser() {
        if (user == null) {
            user = s_entityMgr.findById(User.class, userId);
        }
        return user;
    }

    public String getEventDetails() {
        return eventDetails;
    }

    public void setEventDetails(final String eventDetails) {
        this.eventDetails = eventDetails;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(final String eventType) {
        this.eventType = eventType;
    }

    public String getEventDescription() {
        return eventDescription;
    }

    public void setEventDescription(final String eventDescription) {
        this.eventDescription = eventDescription;
    }

    public Project getProject() {
        return this.project;
    }

    public void setProject(final Project project) {
        this.project = project;
    }

    /**
     * Whether to display the event to the end user.
     *
     * @return true - if the event is to be displayed to the end user, false otherwise.
     */
    public boolean isEventDisplayEnabled() {
        return isEventDisplayEnabled;
    }

    public void setEventDisplayEnabled(final boolean eventDisplayEnabled) {
        isEventDisplayEnabled = eventDisplayEnabled;
    }

    public Map<Object, Object> getContextParameters() {
        return context;
    }

    public void putContextParameters(final Map<Object, Object> details) {
        if (details == null) {
            return;
        }
        for (final Map.Entry<Object, Object> entry : details.entrySet()) {
            putContextParameter(entry.getKey(), entry.getValue());
        }
    }

    public void putContextParameter(final Object key, final Object value) {
        context.put(key, value);
    }

    @Override
    public String toString() {
        return new StringBuilder("CCtxt[acct=").append(getCallingAccountId())
                                               .append("; user=")
                                               .append(getCallingUserId())
                                               .append("; id=")
                                               .append(contextId)
                                               .append("]")
                                               .toString();
    }

    public long getCallingAccountId() {
        return accountId;
    }
}
