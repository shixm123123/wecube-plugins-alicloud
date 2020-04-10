package com.webank.wecube.plugins.alicloud.dto.ecs.securityGroup;

import com.aliyuncs.ecs.model.v20140526.AuthorizeSecurityGroupResponse;
import com.webank.wecube.plugins.alicloud.dto.CoreResponseOutputDto;
import com.webank.wecube.plugins.alicloud.dto.PluginSdkOutputBridge;

/**
 * @author howechen
 */
public class CoreAuthorizeSecurityGroupResponseDto extends CoreResponseOutputDto implements PluginSdkOutputBridge<CoreAuthorizeSecurityGroupResponseDto, AuthorizeSecurityGroupResponse> {
    private String requestId;

    public CoreAuthorizeSecurityGroupResponseDto() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}