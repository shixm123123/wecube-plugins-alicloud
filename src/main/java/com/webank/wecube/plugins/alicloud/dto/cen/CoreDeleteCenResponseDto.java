package com.webank.wecube.plugins.alicloud.dto.cen;

import com.aliyuncs.cbn.model.v20170912.DeleteCenResponse;
import com.webank.wecube.plugins.alicloud.dto.CoreResponseDto;
import org.apache.commons.lang3.StringUtils;

/**
 * @author howechen
 */
public class CoreDeleteCenResponseDto extends DeleteCenResponse {

    private String errorCode = CoreResponseDto.STATUS_OK;
    private String errorMessage = StringUtils.EMPTY;
    private String guid;
    private String callbackParameter;

    public CoreDeleteCenResponseDto() {
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getCallbackParameter() {
        return callbackParameter;
    }

    public void setCallbackParameter(String callbackParameter) {
        this.callbackParameter = callbackParameter;
    }
}
