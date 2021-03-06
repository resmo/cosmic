package com.cloud.api.command.user.autoscale;

import com.cloud.acl.RoleType;
import com.cloud.acl.SecurityChecker.AccessType;
import com.cloud.api.ACL;
import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCustomIdCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.AutoScalePolicyResponse;
import com.cloud.api.response.AutoScaleVmGroupResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.network.as.AutoScaleVmGroup;
import com.cloud.user.Account;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updateAutoScaleVmGroup", description = "Updates an existing autoscale vm group.", responseObject = AutoScaleVmGroupResponse.class, entityType =
        {AutoScaleVmGroup.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateAutoScaleVmGroupCmd extends BaseAsyncCustomIdCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdateAutoScaleVmGroupCmd.class.getName());

    private static final String s_name = "updateautoscalevmgroupresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.MIN_MEMBERS,
            type = CommandType.INTEGER,
            description = "the minimum number of members in the vmgroup, the number of instances in the vm group will be equal to or more than this number.")
    private Integer minMembers;

    @Parameter(name = ApiConstants.MAX_MEMBERS,
            type = CommandType.INTEGER,
            description = "the maximum number of members in the vmgroup, The number of instances in the vm group will be equal to or less than this number.")
    private Integer maxMembers;

    @Parameter(name = ApiConstants.INTERVAL, type = CommandType.INTEGER, description = "the frequency at which the conditions have to be evaluated")
    private Integer interval;

    @Parameter(name = ApiConstants.SCALEUP_POLICY_IDS,
            type = CommandType.LIST,
            collectionType = CommandType.UUID,
            entityType = AutoScalePolicyResponse.class,
            description = "list of scaleup autoscale policies")
    private List<Long> scaleUpPolicyIds;

    @Parameter(name = ApiConstants.SCALEDOWN_POLICY_IDS,
            type = CommandType.LIST,
            collectionType = CommandType.UUID,
            entityType = AutoScalePolicyResponse.class,
            description = "list of scaledown autoscale policies")
    private List<Long> scaleDownPolicyIds;

    @ACL(accessType = AccessType.OperateEntry)
    @Parameter(name = ApiConstants.ID,
            type = CommandType.UUID,
            entityType = AutoScaleVmGroupResponse.class,
            required = true,
            description = "the ID of the autoscale group")
    private Long id;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the group to the end user or not", since =
            "4.4", authorized = {RoleType.Admin})
    private Boolean display;

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public void execute() {
        CallContext.current().setEventDetails("AutoScale Vm Group Id: " + getId());
        final AutoScaleVmGroup result = _autoScaleService.updateAutoScaleVmGroup(this);
        if (result != null) {
            final AutoScaleVmGroupResponse response = _responseGenerator.createAutoScaleVmGroupResponse(result);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update autoscale VmGroup");
        }
    }

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final AutoScaleVmGroup autoScaleVmGroup = _entityMgr.findById(AutoScaleVmGroup.class, getId());
        if (autoScaleVmGroup != null) {
            return autoScaleVmGroup.getAccountId();
        }
        return Account.ACCOUNT_ID_SYSTEM; // no account info given, parent this command to SYSTEM so ERROR events are
        // tracked
    }

    public Integer getMinMembers() {
        return minMembers;
    }

    public Integer getMaxMembers() {
        return maxMembers;
    }

    public Integer getInterval() {
        return interval;
    }

    public List<Long> getScaleUpPolicyIds() {
        return scaleUpPolicyIds;
    }

    public List<Long> getScaleDownPolicyIds() {
        return scaleDownPolicyIds;
    }

    public Boolean getDisplay() {
        return display;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_AUTOSCALEVMGROUP_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return "Updating AutoScale Vm Group. Vm Group Id: " + getId();
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.AutoScaleVmGroup;
    }

    @Override
    public void checkUuid() {
        if (getCustomId() != null) {
            _uuidMgr.checkUuid(getCustomId(), AutoScaleVmGroup.class);
        }
    }
}
