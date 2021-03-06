package com.cloud.api.response;

import com.cloud.api.BaseResponse;
import com.cloud.serializer.Param;

import com.google.gson.annotations.SerializedName;

public class DedicateClusterResponse extends BaseResponse {
    @SerializedName("id")
    @Param(description = "the ID of the dedicated resource")
    private String id;

    @SerializedName("clusterid")
    @Param(description = "the ID of the cluster")
    private String clusterId;

    @SerializedName("clustername")
    @Param(description = "the name of the cluster")
    private String clusterName;

    @SerializedName("domainid")
    @Param(description = "the domain ID of the cluster")
    private String domainId;

    @SerializedName("domainname")
    @Param(description = "the domain name of the host")
    private String domainName;

    @SerializedName("accountid")
    @Param(description = "the Account ID of the cluster")
    private String accountId;

    @SerializedName("accountname")
    @Param(description = "the Account name of the host")
    private String accountName;

    @SerializedName("affinitygroupid")
    @Param(description = "the Dedication Affinity Group ID of the cluster")
    private String affinityGroupId;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(final String clusterId) {
        this.clusterId = clusterId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(final String clusterName) {
        this.clusterName = clusterName;
    }

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(final String domainId) {
        this.domainId = domainId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(final String accountId) {
        this.accountId = accountId;
    }

    public String getAffinityGroupId() {
        return affinityGroupId;
    }

    public void setAffinityGroupId(final String affinityGroupId) {
        this.affinityGroupId = affinityGroupId;
    }

    public void setDomainName(final String domainName) {
        this.domainName = domainName;
    }

    public void setAccountName(final String accountName) {
        this.accountName = accountName;
    }
}
