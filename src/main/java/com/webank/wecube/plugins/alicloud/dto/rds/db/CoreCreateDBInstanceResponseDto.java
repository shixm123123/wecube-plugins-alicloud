package com.webank.wecube.plugins.alicloud.dto.rds.db;

import com.aliyuncs.rds.model.v20140815.CreateDBInstanceResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.webank.wecube.plugins.alicloud.dto.CoreResponseOutputDto;
import com.webank.wecube.plugins.alicloud.dto.PluginSdkOutputBridge;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * @author howechen
 */
public class CoreCreateDBInstanceResponseDto extends CoreResponseOutputDto implements PluginSdkOutputBridge<CoreCreateDBInstanceResponseDto, CreateDBInstanceResponse> {

    private String requestId;
    private String dBInstanceId;
    private String orderId;
    private String connectionString;
    private String port;

    // RDS account info
    private String accountName;
    @JsonProperty(value = "accountPassword")
    private String accountEncryptedPassword;

    public CoreCreateDBInstanceResponseDto() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getDBInstanceId() {
        return dBInstanceId;
    }

    public void setDBInstanceId(String dBInstanceId) {
        this.dBInstanceId = dBInstanceId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountEncryptedPassword() {
        return accountEncryptedPassword;
    }

    public void setAccountEncryptedPassword(String accountEncryptedPassword) {
        this.accountEncryptedPassword = accountEncryptedPassword;
    }

    public CoreCreateDBInstanceResponseDto fromSdk(CreateDBInstanceResponse response, String accountName, String accountEncryptedPassword) {
        final CoreCreateDBInstanceResponseDto result = this.fromSdk(response);
        result.setAccountName(accountName);
        result.setAccountEncryptedPassword(accountEncryptedPassword);
        return result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.JSON_STYLE)
                .appendSuper(super.toString())
                .append("requestId", requestId)
                .append("dBInstanceId", dBInstanceId)
                .append("orderId", orderId)
                .append("connectionString", connectionString)
                .append("port", port)
                .append("accountName", accountName)
                .append("accountEncryptedPassword", accountEncryptedPassword)
                .toString();
    }
}
