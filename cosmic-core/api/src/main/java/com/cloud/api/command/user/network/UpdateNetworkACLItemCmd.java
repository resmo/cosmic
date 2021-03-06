package com.cloud.api.command.user.network;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCustomIdCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.NetworkACLItemResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.vpc.NetworkACLItem;
import com.cloud.user.Account;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updateNetworkACLItem", description = "Updates ACL item with specified ID", responseObject = NetworkACLItemResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateNetworkACLItemCmd extends BaseAsyncCustomIdCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdateNetworkACLItemCmd.class.getName());

    private static final String s_name = "createnetworkaclresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID,
            type = CommandType.UUID,
            entityType = NetworkACLItemResponse.class,
            required = true,
            description = "the ID of the network ACL item")
    private Long id;

    @Parameter(name = ApiConstants.PROTOCOL,
            type = CommandType.STRING,
            description = "the protocol for the ACL rule. Valid values are TCP/UDP/ICMP/ALL or valid protocol number")
    private String protocol;

    @Parameter(name = ApiConstants.START_PORT, type = CommandType.INTEGER, description = "the starting port of ACL")
    private Integer publicStartPort;

    @Parameter(name = ApiConstants.END_PORT, type = CommandType.INTEGER, description = "the ending port of ACL")
    private Integer publicEndPort;

    @Parameter(name = ApiConstants.CIDR_LIST, type = CommandType.LIST, collectionType = CommandType.STRING, description = "the cidr list to allow traffic from/to")
    private List<String> cidrlist;

    @Parameter(name = ApiConstants.ICMP_TYPE, type = CommandType.INTEGER, description = "type of the ICMP message being sent")
    private Integer icmpType;

    @Parameter(name = ApiConstants.ICMP_CODE, type = CommandType.INTEGER, description = "error code for this ICMP message")
    private Integer icmpCode;

    @Parameter(name = ApiConstants.TRAFFIC_TYPE, type = CommandType.STRING, description = "the traffic type for the ACL,"
            + "can be Ingress or Egress, defaulted to Ingress if not specified")
    private String trafficType;

    @Parameter(name = ApiConstants.NUMBER, type = CommandType.INTEGER, description = "The network of the vm the ACL will be created for")
    private Integer number;

    @Parameter(name = ApiConstants.ACTION, type = CommandType.STRING, description = "scl entry action, allow or deny")
    private String action;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the rule to the end user or not", since = "4" +
            ".4", authorized = {RoleType.Admin})
    private Boolean display;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getEventType() {
        return EventTypes.EVENT_NETWORK_ACL_ITEM_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return "Updating network ACL item";
    }

    @Override
    public void execute() throws ResourceUnavailableException {
        CallContext.current().setEventDetails("Rule Id: " + getId());
        final NetworkACLItem aclItem =
                _networkACLService.updateNetworkACLItem(getId(), getProtocol(), getSourceCidrList(), getTrafficType(), getAction(), getNumber(), getSourcePortStart(),
                        getSourcePortEnd(), getIcmpCode(), getIcmpType(), this.getCustomId(), this.isDisplay());
        if (aclItem == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update network ACL item");
        }
        final NetworkACLItemResponse aclResponse = _responseGenerator.createNetworkACLItemResponse(aclItem);
        setResponseObject(aclResponse);
        aclResponse.setResponseName(getCommandName());
    }

    public Long getId() {
        return id;
    }

    public String getProtocol() {
        if (protocol != null) {
            return protocol.trim();
        } else {
            return null;
        }
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    public List<String> getSourceCidrList() {
        return cidrlist;
    }

    public NetworkACLItem.TrafficType getTrafficType() {
        if (trafficType != null) {
            for (final NetworkACLItem.TrafficType type : NetworkACLItem.TrafficType.values()) {
                if (type.toString().equalsIgnoreCase(trafficType)) {
                    return type;
                }
            }
        }
        return null;
    }

    public String getAction() {
        return action;
    }

    public Integer getNumber() {
        return number;
    }

    public Integer getSourcePortStart() {
        return publicStartPort;
    }

    public Integer getSourcePortEnd() {
        return publicEndPort;
    }

    public Integer getIcmpCode() {
        return icmpCode;
    }

    public Integer getIcmpType() {
        return icmpType;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Account caller = CallContext.current().getCallingAccount();
        return caller.getAccountId();
    }

    @Override
    public boolean isDisplay() {
        if (display != null) {
            return display;
        } else {
            return true;
        }
    }

    @Override
    public void checkUuid() {
        if (this.getCustomId() != null) {
            _uuidMgr.checkUuid(this.getCustomId(), NetworkACLItem.class);
        }
    }
}
