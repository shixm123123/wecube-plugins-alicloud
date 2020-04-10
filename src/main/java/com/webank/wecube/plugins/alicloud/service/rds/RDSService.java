package com.webank.wecube.plugins.alicloud.service.rds;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.rds.model.v20140815.CreateAccountRequest;
import com.webank.wecube.plugins.alicloud.common.PluginException;
import com.webank.wecube.plugins.alicloud.dto.rds.backup.CoreCreateBackupRequestDto;
import com.webank.wecube.plugins.alicloud.dto.rds.backup.CoreCreateBackupResponseDto;
import com.webank.wecube.plugins.alicloud.dto.rds.backup.CoreDeleteBackupRequestDto;
import com.webank.wecube.plugins.alicloud.dto.rds.backup.CoreDeleteBackupResponseDto;
import com.webank.wecube.plugins.alicloud.dto.rds.db.CoreCreateDBInstanceRequestDto;
import com.webank.wecube.plugins.alicloud.dto.rds.db.CoreCreateDBInstanceResponseDto;
import com.webank.wecube.plugins.alicloud.dto.rds.db.CoreDeleteDBInstanceRequestDto;
import com.webank.wecube.plugins.alicloud.dto.rds.db.CoreDeleteDBInstanceResponseDto;
import com.webank.wecube.plugins.alicloud.dto.rds.securityIP.CoreModifySecurityIPsRequestDto;
import com.webank.wecube.plugins.alicloud.dto.rds.securityIP.CoreModifySecurityIPsResponseDto;
import com.webank.wecube.plugins.alicloud.support.AliCloudException;

import java.util.List;

/**
 * @author howechen
 */
public interface RDSService {
    List<CoreCreateDBInstanceResponseDto> createDB(List<CoreCreateDBInstanceRequestDto> requestDtoList) throws PluginException;

    List<CoreDeleteDBInstanceResponseDto> deleteDB(List<CoreDeleteDBInstanceRequestDto> requestDtoList) throws PluginException;

    List<CoreModifySecurityIPsResponseDto> modifySecurityIPs(List<CoreModifySecurityIPsRequestDto> requestDtoList) throws PluginException;

    List<CoreCreateBackupResponseDto> createBackup(List<CoreCreateBackupRequestDto> requestDtoList) throws PluginException;

    List<CoreDeleteBackupResponseDto> deleteBackup(List<CoreDeleteBackupRequestDto> requestDtoList);

    Boolean ifDBInstanceInStatus(IAcsClient client, String regionId, String dbInstanceId, RDSStatus status) throws PluginException, AliCloudException;

    Boolean ifBackupTaskInStatus(IAcsClient client, String regionId, String dbInstanceId, String backupJobId, BackupStatus status) throws PluginException, AliCloudException;

    void createRDSAccount(IAcsClient client, CreateAccountRequest createAccountRequest) throws PluginException, AliCloudException;

    Boolean ifRDSAccountCreated(IAcsClient client, String regionId, String accountName, String dBInstanceId) throws PluginException, AliCloudException;

}