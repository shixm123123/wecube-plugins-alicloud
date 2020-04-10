package com.webank.wecube.plugins.alicloud.dto;

import com.webank.wecube.plugins.alicloud.utils.PluginMapUtils;

import java.util.Map;

/**
 * @author howechen
 */
public class IdentityParamDto {
    private String accessKeyId;
    private String secret;

    public IdentityParamDto(String accessKeyId, String secret) {
        this.accessKeyId = accessKeyId;
        this.secret = secret;
    }

    public IdentityParamDto() {
    }

    public static IdentityParamDto convertFromString(String paramStr) {
        final Map<String, String> map = PluginMapUtils.fromCoreParamString(paramStr);
        return new IdentityParamDto(map.get("accessKeyId"), map.get("secret"));
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}