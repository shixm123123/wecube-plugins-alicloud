package com.webank.wecube.plugins.alicloud.service.rds;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.rds.model.v20140815.*;
import com.webank.wecube.plugins.alicloud.common.PluginException;
import com.webank.wecube.plugins.alicloud.dto.CloudParamDto;
import com.webank.wecube.plugins.alicloud.dto.CoreResponseDto;
import com.webank.wecube.plugins.alicloud.dto.IdentityParamDto;
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
import com.webank.wecube.plugins.alicloud.service.redis.InstanceStatus;
import com.webank.wecube.plugins.alicloud.support.AcsClientStub;
import com.webank.wecube.plugins.alicloud.support.AliCloudException;
import com.webank.wecube.plugins.alicloud.support.DtoValidator;
import com.webank.wecube.plugins.alicloud.support.PluginSdkBridge;
import com.webank.wecube.plugins.alicloud.support.password.PasswordManager;
import com.webank.wecube.plugins.alicloud.support.timer.PluginTimer;
import com.webank.wecube.plugins.alicloud.support.timer.PluginTimerTask;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author howechen
 */
@Service
public class RDSServiceImpl implements RDSService {

    private static Logger logger = LoggerFactory.getLogger(RDSService.class);

    private AcsClientStub acsClientStub;
    private DtoValidator dtoValidator;
    private PasswordManager passwordManager;

    @Autowired
    public RDSServiceImpl(AcsClientStub acsClientStub, DtoValidator dtoValidator, PasswordManager passwordManager) {
        this.acsClientStub = acsClientStub;
        this.dtoValidator = dtoValidator;
        this.passwordManager = passwordManager;
    }

    @Override
    public List<CoreCreateDBInstanceResponseDto> createDB(List<CoreCreateDBInstanceRequestDto> requestDtoList) throws PluginException {
        List<CoreCreateDBInstanceResponseDto> resultList = new ArrayList<>();
        for (CoreCreateDBInstanceRequestDto requestDto : requestDtoList) {

            CoreCreateDBInstanceResponseDto result = new CoreCreateDBInstanceResponseDto();

            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final String regionId = cloudParamDto.getRegionId();
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);

                if (StringUtils.isNotEmpty(requestDto.getDBInstanceId())) {
                    final String instanceId = requestDto.getDBInstanceId();
                    final DescribeDBInstancesResponse retrieveDBInstance = this.retrieveDBInstance(client, regionId, instanceId);
                    if (1 == retrieveDBInstance.getTotalRecordCount()) {
                        final DescribeDBInstancesResponse.DBInstance dbInstance = retrieveDBInstance.getItems().get(0);
                        result = result.fromSdkCrossLineage(dbInstance);
                        result.setRequestId(retrieveDBInstance.getRequestId());
                        continue;
                    }

                }

                logger.info("Creating DB instance....");

                final CreateDBInstanceRequest createDBInstanceRequest = requestDto.toSdk();
                createDBInstanceRequest.setRegionId(regionId);
                CreateDBInstanceResponse response;
                response = this.acsClientStub.request(client, createDBInstanceRequest);

                // set up Plugin Timer to check if the resource is not creating any more
                final String createdDBInstanceId = response.getDBInstanceId();
                Function<?, Boolean> func = o -> !ifDBInstanceInStatus(client, regionId, createdDBInstanceId, RDSStatus.CREATING);
                PluginTimer.runTask(new PluginTimerTask(func));

                // create an RDS account with just created DB instance bound onto
                logger.info("Creating RDS account and bind it to the just created DB instance");

                final CreateAccountRequest createAccountRequest = PluginSdkBridge.toSdk(requestDto, CreateAccountRequest.class, true);

                String password = createAccountRequest.getAccountPassword();
                if (StringUtils.isEmpty(password)) {
                    password = passwordManager.generateRDSPassword();
                    createAccountRequest.setAccountPassword(password);
                }

                final String encryptedPassword = passwordManager.encryptPassword(requestDto.getGuid(), requestDto.getSeed(), password);

                createAccountRequest.setRegionId(regionId);
                createAccountRequest.setDBInstanceId(createdDBInstanceId);
                createRDSAccount(client, createAccountRequest);

                // return result
                result = result.fromSdk(response, requestDto.getAccountName(), encryptedPassword);


            } catch (PluginException | AliCloudException ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setErrorMessage(ex.getMessage());
            } finally {
                result.setGuid(requestDto.getGuid());
                result.setCallbackParameter(requestDto.getCallbackParameter());
                resultList.add(result);
            }


        }
        return resultList;
    }

    @Override
    public List<CoreDeleteDBInstanceResponseDto> deleteDB(List<CoreDeleteDBInstanceRequestDto> requestDtoList) throws PluginException {
        List<CoreDeleteDBInstanceResponseDto> resultList = new ArrayList<>();
        for (CoreDeleteDBInstanceRequestDto requestDto : requestDtoList) {

            CoreDeleteDBInstanceResponseDto result = new CoreDeleteDBInstanceResponseDto();

            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();

                final String dbInstanceId = requestDto.getDBInstanceId();

                if (StringUtils.isAnyEmpty(regionId, dbInstanceId)) {
                    throw new PluginException("Either the regionId or dbInstanceID cannot be empty or null.");
                }

                DescribeDBInstancesResponse describeDBInstancesResponse = this.retrieveDBInstance(client, regionId, dbInstanceId);
                if (0 == describeDBInstancesResponse.getTotalRecordCount()) {
                    logger.info("The given db instance has already been released...");
                    result.setRequestId(describeDBInstancesResponse.getRequestId());
                    continue;
                }

                logger.info("Deleting DB instance...");

                final DeleteDBInstanceRequest deleteDBInstanceRequest = requestDto.toSdk();
                deleteDBInstanceRequest.setRegionId(regionId);
                DeleteDBInstanceResponse response;
                response = this.acsClientStub.request(client, deleteDBInstanceRequest);


                // set up Plugin Timer to check if the resource has been deleted
                Function<?, Boolean> func = o -> ifDBInstanceIsDeleted(client, regionId, requestDto.getDBInstanceId());
                PluginTimer.runTask(new PluginTimerTask(func));

                result = result.fromSdk(response);

            } catch (PluginException | AliCloudException ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setErrorMessage(ex.getMessage());
            } finally {
                result.setGuid(requestDto.getGuid());
                result.setCallbackParameter(requestDto.getCallbackParameter());
                resultList.add(result);
            }
        }
        return resultList;
    }

    @Override
    public List<CoreModifySecurityIPsResponseDto> modifySecurityIPs(List<CoreModifySecurityIPsRequestDto> requestDtoList) throws PluginException {
        List<CoreModifySecurityIPsResponseDto> resultList = new ArrayList<>();
        for (CoreModifySecurityIPsRequestDto requestDto : requestDtoList) {

            CoreModifySecurityIPsResponseDto result = new CoreModifySecurityIPsResponseDto();
            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();


                if (StringUtils.isAnyEmpty(regionId, requestDto.getSecurityIps())) {
                    throw new PluginException("Either regionId or security IP cannot be null or empty.");
                }

                final ModifySecurityIpsRequest request = requestDto.toSdk();
                request.setRegionId(regionId);
                ModifySecurityIpsResponse response;
                response = this.acsClientStub.request(client, request);

                result = result.fromSdk(response);

            } catch (PluginException | AliCloudException ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setErrorMessage(ex.getMessage());
            } finally {
                result.setGuid(requestDto.getGuid());
                result.setCallbackParameter(requestDto.getCallbackParameter());
                resultList.add(result);
            }
        }
        return resultList;
    }

    @Override
    public List<CoreCreateBackupResponseDto> createBackup(List<CoreCreateBackupRequestDto> requestDtoList) throws PluginException {
        List<CoreCreateBackupResponseDto> resultList = new ArrayList<>();
        for (CoreCreateBackupRequestDto requestDto : requestDtoList) {

            CoreCreateBackupResponseDto result = new CoreCreateBackupResponseDto();

            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final String regionId = cloudParamDto.getRegionId();
                final String dbInstanceId = requestDto.getDBInstanceId();
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String backupId = requestDto.getBackupId();

                if (StringUtils.isNotEmpty(backupId)) {

                    final DescribeBackupsResponse describeBackupsResponse = this.retrieveBackups(client, regionId, dbInstanceId, backupId);
                    if (StringUtils.isNotEmpty(describeBackupsResponse.getTotalRecordCount())) {
                        final DescribeBackupsResponse.Backup foundBackup = describeBackupsResponse.getItems().get(0);
                        result = result.fromSdkCrossLineage(foundBackup);
                        result.setRequestId(describeBackupsResponse.getRequestId());
                        continue;
                    }

                }

                logger.info("Creating backup....");

                final CreateBackupRequest createDBInstanceRequest = requestDto.toSdk();
                createDBInstanceRequest.setRegionId(regionId);
                CreateBackupResponse response;
                response = this.acsClientStub.request(client, createDBInstanceRequest);

                final String backupJobId = response.getBackupJobId();

                // wait for the backup job to be finished
                Function<?, Boolean> func = o -> this.ifBackupTaskInStatus(client, regionId, dbInstanceId, backupJobId, BackupStatus.FINISHED);
                PluginTimer.runTask(new PluginTimerTask(func));

                // retrieve backup info according to the backup job id from the AliCloud
                // only will return backup id when backup task status has been set to finished
                final DescribeBackupTasksResponse.BackupJob backupJob = this.retrieveBackupFromJobId(client, dbInstanceId, backupJobId);

                result = result.fromSdkCrossLineage(backupJob);
                result.setRequestId(response.getRequestId());

            } catch (PluginException | AliCloudException ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setErrorMessage(ex.getMessage());
            } finally {
                result.setGuid(requestDto.getGuid());
                result.setCallbackParameter(requestDto.getCallbackParameter());
                resultList.add(result);
            }
        }
        return resultList;
    }

    @Override
    public List<CoreDeleteBackupResponseDto> deleteBackup(List<CoreDeleteBackupRequestDto> requestDtoList) {
        List<CoreDeleteBackupResponseDto> resultList = new ArrayList<>();
        for (CoreDeleteBackupRequestDto requestDto : requestDtoList) {

            CoreDeleteBackupResponseDto result = new CoreDeleteBackupResponseDto();
            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();

                final String dbInstanceId = requestDto.getDBInstanceId();
                String backupId = requestDto.getBackupId();
                final String backupJobId = requestDto.getBackupJobId();

                // if backup job exists, get backupId through job ID first
                if (StringUtils.isNotEmpty(backupJobId) && StringUtils.isEmpty(backupId)) {
                    // retrieve backupId through backupJobId
                    final DescribeBackupTasksResponse.BackupJob backup = this.retrieveBackupFromJobId(client, dbInstanceId, backupJobId);
                    backupId = backup.getBackupId();
                }

                DescribeBackupsResponse describeBackupsResponse = this.retrieveBackups(client, regionId, dbInstanceId, backupId);

                if (StringUtils.isEmpty(describeBackupsResponse.getTotalRecordCount())) {
                    result.setRequestId(describeBackupsResponse.getRequestId());
                    logger.info("The backup has already been deleted...");
                }

                logger.info("Deleting backup...");

                final DeleteBackupRequest deleteBackupRequest = requestDto.toSdk();
                deleteBackupRequest.setRegionId(regionId);
                deleteBackupRequest.setBackupId(backupId);
                DeleteBackupResponse response = this.acsClientStub.request(client, deleteBackupRequest);

                // setup a timer task to retrieve if the backup has been deleted
                final String finalBackupId = backupId;
                Function<?, Boolean> func = o -> this.ifBackupHasBeenDeleted(client, regionId, dbInstanceId, finalBackupId);
                PluginTimer.runTask(new PluginTimerTask(func));

                result = result.fromSdk(response);

            } catch (PluginException | AliCloudException ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setErrorMessage(ex.getMessage());
            } finally {
                result.setGuid(requestDto.getGuid());
                result.setCallbackParameter(requestDto.getCallbackParameter());
                resultList.add(result);
            }

        }
        return resultList;
    }

    @Override
    public Boolean ifDBInstanceInStatus(IAcsClient client, String regionId, String dbInstanceId, RDSStatus status) throws PluginException, AliCloudException {
        if (StringUtils.isAnyEmpty(regionId, dbInstanceId)) {
            throw new PluginException("Either regionId or instanceId cannot be null or empty.");
        }

        DescribeDBInstancesRequest request = new DescribeDBInstancesRequest();
        request.setRegionId(regionId);
        request.setDBInstanceId(dbInstanceId);


        final DescribeDBInstancesResponse response = this.acsClientStub.request(client, request);

        final Optional<DescribeDBInstancesResponse.DBInstance> foundDBInstanceOpt = response.getItems().stream().filter(dbInstance -> StringUtils.equals(dbInstanceId, dbInstance.getDBInstanceId())).findFirst();

        foundDBInstanceOpt.orElseThrow(() -> new PluginException(String.format("Cannot found DB instance by instanceId: [%s]", dbInstanceId)));

        final DescribeDBInstancesResponse.DBInstance dbInstance = foundDBInstanceOpt.get();

        return StringUtils.equals(status.getStatus(), dbInstance.getDBInstanceStatus());
    }

    @Override
    public Boolean ifBackupTaskInStatus(IAcsClient client, String regionId, String dbInstanceId, String backupJobId, BackupStatus status) throws PluginException, AliCloudException {
        if (StringUtils.isAnyEmpty(regionId, backupJobId)) {
            throw new PluginException("Either regionId or instanceId cannot be null or empty.");
        }

        DescribeBackupTasksRequest request = new DescribeBackupTasksRequest();
        request.setRegionId(regionId);
        request.setDBInstanceId(dbInstanceId);
        request.setBackupJobId(backupJobId);


        final DescribeBackupTasksResponse response = this.acsClientStub.request(client, request);

        final Optional<DescribeBackupTasksResponse.BackupJob> foundBackupJobOpt = response.getItems().stream().filter(backupTask -> StringUtils.equals(backupJobId, backupTask.getBackupJobId())).findFirst();

        foundBackupJobOpt.orElseThrow(() -> new PluginException(String.format("Cannot found backup job by dbInstanceId: [%s] and backupJobId: [%s]", dbInstanceId, backupJobId)));

        final DescribeBackupTasksResponse.BackupJob backupJob = foundBackupJobOpt.get();

        return StringUtils.equals(status.getStatus(), backupJob.getBackupStatus());
    }

    @Override
    public void createRDSAccount(IAcsClient client, CreateAccountRequest createAccountRequest) throws PluginException, AliCloudException {

        logger.info("Creating RDS account...");

        if (StringUtils.isEmpty(createAccountRequest.getRegionId())) {
            throw new PluginException("The regionId cannot be null or empty.");
        }

        this.acsClientStub.request(client, createAccountRequest);

        // check if the account has been created
        Function<?, Boolean> func = o -> ifRDSAccountCreated(client, createAccountRequest.getRegionId(), createAccountRequest.getAccountName(), createAccountRequest.getDBInstanceId());
        PluginTimer.runTask(new PluginTimerTask(func));
    }

    @Override
    public Boolean ifRDSAccountCreated(IAcsClient client, String regionId, String accountName, String dBInstanceId) throws PluginException, AliCloudException {

        boolean ifAccountCreated = false;

        logger.info("Retrieving RDS account info...");

        if (StringUtils.isAnyEmpty(regionId, accountName)) {
            throw new PluginException("Either regionId or accountName cannot be null or empty.");
        }

        DescribeAccountsRequest request = new DescribeAccountsRequest();
        request.setRegionId(regionId);
        request.setAccountName(accountName);
        request.setDBInstanceId(dBInstanceId);

        final DescribeAccountsResponse response = this.acsClientStub.request(client, request);

        final long count = response.getAccounts().stream().filter(dbInstanceAccount -> StringUtils.equals(accountName, dbInstanceAccount.getAccountName())).count();

        if (count == 1) {
            ifAccountCreated = true;
        }

        return ifAccountCreated;

    }

    private Boolean ifDBInstanceIsDeleted(IAcsClient client, String regionId, String dBInstanceId) throws PluginException, AliCloudException {
        if (StringUtils.isAnyEmpty(regionId, dBInstanceId)) {
            throw new PluginException("Either regionId or dBInstanceId cannot be null or empty.");
        }

        DescribeDBInstancesResponse response;
        try {
            response = this.retrieveDBInstance(client, regionId, dBInstanceId);
        } catch (AliCloudException ex) {
            // AliCloud's RDS will throw error when the resource has been deleted.
            if (ex.getMessage().contains("The specified instance is not found.")) {
                return true;
            } else {
                throw ex;
            }
        }
        return response.getTotalRecordCount() == 0;
    }

    private Boolean ifBackupHasBeenDeleted(IAcsClient client, String regionId, String dBInstanceId, String backupId) throws PluginException, AliCloudException {
        if (StringUtils.isAnyEmpty(regionId, dBInstanceId, backupId)) {
            throw new PluginException("Either regionId or dBInstanceId cannot be null or empty.");
        }

        DescribeBackupsResponse response;
        try {
            response = this.retrieveBackups(client, regionId, dBInstanceId, backupId);
        } catch (AliCloudException ex) {
            // AliCloud's RDS will throw error when the resource has been deleted.
            if (ex.getMessage().contains("The specified instance is not found.")) {
                return true;
            } else {
                throw ex;
            }
        }
        return 0 == Integer.parseInt(response.getTotalRecordCount());
    }


    private DescribeBackupTasksResponse.BackupJob retrieveBackupFromJobId(IAcsClient client, String dbInstanceId, String backupJobId) throws PluginException, AliCloudException {
        DescribeBackupTasksRequest retrieveTasksRequest = new DescribeBackupTasksRequest();
        retrieveTasksRequest.setDBInstanceId(dbInstanceId);
        retrieveTasksRequest.setBackupJobId(backupJobId);
        DescribeBackupTasksResponse retrieveTasksRepsonse;
        retrieveTasksRepsonse = this.acsClientStub.request(client, retrieveTasksRequest);

        final List<DescribeBackupTasksResponse.BackupJob> foundBackupsList = retrieveTasksRepsonse.getItems();

        if (foundBackupsList.isEmpty()) {
            throw new PluginException("Cannot find backup through job ID");
        }

        DescribeBackupTasksResponse.BackupJob result;
        if (1 == foundBackupsList.size()) {
            result = foundBackupsList.get(0);
        } else {
            throw new PluginException(String.format("Error! Found multiple backups from one Job ID: [%s]", backupJobId));
        }

        return result;
    }

    private DescribeDBInstancesResponse retrieveDBInstance(IAcsClient client, String regionId, String dbInstanceId) throws PluginException, AliCloudException {
        if (StringUtils.isAnyEmpty(regionId, dbInstanceId)) {
            throw new PluginException("Either regionId or dbInstanceID cannot be null or empty");
        }

        logger.info("Retrieving dbInstance info...");

        DescribeDBInstancesRequest request = new DescribeDBInstancesRequest();
        request.setRegionId(regionId);
        request.setDBInstanceId(dbInstanceId);

        DescribeDBInstancesResponse response;
        response = this.acsClientStub.request(client, request);
        return response;
    }

    private DescribeBackupsResponse retrieveBackups(IAcsClient client, String regionId, String dbInstanceId, String backupId) throws PluginException, AliCloudException {
        if (StringUtils.isAnyEmpty(regionId, dbInstanceId, backupId)) {
            throw new PluginException("Either regionId, dbInstanceID or backupId cannot be null or empty");
        }

        logger.info("Retrieving backup info...");

        DescribeBackupsRequest request = new DescribeBackupsRequest();
        request.setRegionId(regionId);
        request.setDBInstanceId(dbInstanceId);
        request.setBackupId(backupId);

        DescribeBackupsResponse response;
        response = this.acsClientStub.request(client, request);
        return response;
    }

}