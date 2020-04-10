package com.webank.wecube.plugins.alicloud.dto.loadBalancer.backendServer;

import com.aliyuncs.slb.model.v20140515.RemoveVServerGroupBackendServersRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.webank.wecube.plugins.alicloud.dto.CoreRequestInputDto;
import com.webank.wecube.plugins.alicloud.dto.PluginSdkInputBridge;

/**
 * @author howechen
 */
public class CoreRemoveBackendServerRequestDto extends CoreRequestInputDto implements PluginSdkInputBridge<RemoveVServerGroupBackendServersRequest> {

    // fields from core
    private String hostIds;
    private String hostPorts;

    // alicloud fields
    private String listenerPort;
    private String loadBalancerId;
    private String listenerProtocol;

    private String resourceOwnerId;
    @JsonIgnore
    private String backendServers;
    private String vServerGroupId;
    private String resourceOwnerAccount;
    private String ownerAccount;
    private String ownerId;

    public CoreRemoveBackendServerRequestDto() {
    }

    public String getListenerPort() {
        return listenerPort;
    }

    public void setListenerPort(String listenerPort) {
        this.listenerPort = listenerPort;
    }

    public String getLoadBalancerId() {
        return loadBalancerId;
    }

    public void setLoadBalancerId(String loadBalancerId) {
        this.loadBalancerId = loadBalancerId;
    }

    public String getListenerProtocol() {
        return listenerProtocol;
    }

    public void setListenerProtocol(String listenerProtocol) {
        this.listenerProtocol = listenerProtocol;
    }

    public String getResourceOwnerId() {
        return resourceOwnerId;
    }

    public void setResourceOwnerId(String resourceOwnerId) {
        this.resourceOwnerId = resourceOwnerId;
    }

    public String getBackendServers() {
        return backendServers;
    }

    public void setBackendServers(String backendServers) {
        this.backendServers = backendServers;
    }

    public String getvServerGroupId() {
        return vServerGroupId;
    }

    public void setvServerGroupId(String vServerGroupId) {
        this.vServerGroupId = vServerGroupId;
    }

    public String getResourceOwnerAccount() {
        return resourceOwnerAccount;
    }

    public void setResourceOwnerAccount(String resourceOwnerAccount) {
        this.resourceOwnerAccount = resourceOwnerAccount;
    }

    public String getOwnerAccount() {
        return ownerAccount;
    }

    public void setOwnerAccount(String ownerAccount) {
        this.ownerAccount = ownerAccount;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getHostIds() {
        return hostIds;
    }

    public void setHostIds(String hostIds) {
        this.hostIds = hostIds;
    }

    public String getHostPorts() {
        return hostPorts;
    }

    public void setHostPorts(String hostPorts) {
        this.hostPorts = hostPorts;
    }
}