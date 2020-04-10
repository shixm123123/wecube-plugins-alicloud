package com.webank.wecube.plugins.alicloud.service.cbn.cen;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.cbn.model.v20170912.*;
import com.webank.wecube.plugins.alicloud.common.PluginException;
import com.webank.wecube.plugins.alicloud.dto.CloudParamDto;
import com.webank.wecube.plugins.alicloud.dto.CoreResponseDto;
import com.webank.wecube.plugins.alicloud.dto.IdentityParamDto;
import com.webank.wecube.plugins.alicloud.dto.cbn.cen.*;
import com.webank.wecube.plugins.alicloud.support.AcsClientStub;
import com.webank.wecube.plugins.alicloud.support.AliCloudException;
import com.webank.wecube.plugins.alicloud.support.DtoValidator;
import com.webank.wecube.plugins.alicloud.support.PluginSdkBridge;
import com.webank.wecube.plugins.alicloud.support.timer.PluginTimer;
import com.webank.wecube.plugins.alicloud.support.timer.PluginTimerTask;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author howechen
 */
@Service
public class CenServiceImpl implements CenService {

    private static final Logger logger = LoggerFactory.getLogger(CenService.class);

    private AcsClientStub acsClientStub;
    private DtoValidator validator;

    @Autowired
    public CenServiceImpl(AcsClientStub acsClientStub, DtoValidator validator) {
        this.acsClientStub = acsClientStub;
        this.validator = validator;
    }

    @Override
    public List<CoreCreateCenResponseDto> createCen(List<CoreCreateCenRequestDto> requestDtoList) {
        List<CoreCreateCenResponseDto> resultList = new ArrayList<>();
        for (CoreCreateCenRequestDto requestDto : requestDtoList) {
            CoreCreateCenResponseDto result = new CoreCreateCenResponseDto();
            try {

                validator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();

                final String cenId = requestDto.getCenId();
                if (StringUtils.isNotEmpty(cenId)) {
                    final DescribeCensResponse describeCensResponse = this.retrieveCen(client);
                    final List<DescribeCensResponse.Cen> foundCenList = describeCensResponse.getCens().stream().filter(cen -> cenId.equals(cen.getCenId())).collect(Collectors.toList());

                    if (foundCenList.size() == 1) {
                        final DescribeCensResponse.Cen cen = foundCenList.get(0);
                        result = result.fromSdkCrossLineage(cen);
                        result.setRequestId(describeCensResponse.getRequestId());
                        continue;
                    }

                }

                logger.info("Creating Cen...");

                CreateCenRequest request = requestDto.toSdk();
                request.setRegionId(regionId);
                CreateCenResponse response = this.acsClientStub.request(client, request);
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
    public List<CoreDeleteCenResponseDto> deleteCen(List<CoreDeleteCenRequestDto> requestDtoList) {
        List<CoreDeleteCenResponseDto> resultList = new ArrayList<>();
        for (CoreDeleteCenRequestDto requestDto : requestDtoList) {
            CoreDeleteCenResponseDto result = new CoreDeleteCenResponseDto();

            try {

                validator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();

                final String cenId = requestDto.getCenId();
                if (StringUtils.isNotEmpty(cenId)) {
                    final DescribeCensResponse describeCensResponse = this.retrieveCen(client);
                    final List<DescribeCensResponse.Cen> foundCenList = describeCensResponse.getCens().stream().filter(cen -> cenId.equals(cen.getCenId())).collect(Collectors.toList());

                    if (foundCenList.isEmpty()) {
                        logger.info("The cen given by ID: [{}] has already been deleted.", cenId);
                        result.setRequestId(describeCensResponse.getRequestId());
                        continue;
                    }
                }

                // check if cen has child instances

                final DescribeCenAttachedChildInstancesResponse describeCenAttachedChildInstancesResponse = this.retrieveCenAttachedChildInstance(client, cenId);
                final List<DescribeCenAttachedChildInstancesResponse.ChildInstance> childInstances = describeCenAttachedChildInstancesResponse.getChildInstances();
                if (!childInstances.isEmpty()) {
                    detachAllChildInstances(client, cenId, childInstances);
                }

                logger.info("Deleting Cen...");

                final DeleteCenRequest deleteCenRequest = requestDto.toSdk();
                final DeleteCenResponse response = this.acsClientStub.request(client, deleteCenRequest);
                result = result.fromSdk(response);

                // check if cen has already been deleted
                Function<?, Boolean> func = o -> this.checkIfCenHasBeenDeleted(client, cenId);
                PluginTimer.runTask(new PluginTimerTask(func));

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

    private void detachAllChildInstances(IAcsClient client, String cenId, List<DescribeCenAttachedChildInstancesResponse.ChildInstance> childInstances) throws PluginException, AliCloudException {
        logger.info("Detaching all child instance first.");

        for (DescribeCenAttachedChildInstancesResponse.ChildInstance childInstance : childInstances) {
            final DetachCenChildInstanceRequest detachCenChildInstanceRequest = PluginSdkBridge.toSdk(childInstance, DetachCenChildInstanceRequest.class);
            detachCenChildInstanceRequest.setCenId(cenId);
            this.acsClientStub.request(client, detachCenChildInstanceRequest);
        }

        Function<?, Boolean> func = o -> this.checkIfCenHasNoChildInstance(client, cenId);
        PluginTimer.runTask(new PluginTimerTask(func));
    }

    @Override
    public List<CoreAttachCenChildResponseDto> attachCenChild(List<CoreAttachCenChildRequestDto> requestDtoList) {
        List<CoreAttachCenChildResponseDto> resultList = new ArrayList<>();
        for (CoreAttachCenChildRequestDto requestDto : requestDtoList) {
            CoreAttachCenChildResponseDto result = new CoreAttachCenChildResponseDto();

            try {

                validator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();

                logger.info("Attaching Cen child instance...");

                AttachCenChildInstanceRequest request = requestDto.toSdk();
                request.setRegionId(regionId);
                AttachCenChildInstanceResponse response = this.acsClientStub.request(client, request);
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
    public List<CoreDetachCenChildResponseDto> detachCenChild(List<CoreDetachCenChildRequestDto> requestDtoList) {
        List<CoreDetachCenChildResponseDto> resultList = new ArrayList<>();
        for (CoreDetachCenChildRequestDto requestDto : requestDtoList) {
            CoreDetachCenChildResponseDto result = new CoreDetachCenChildResponseDto();

            try {

                validator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();


                logger.info("Detaching Cen child instance...");

                DetachCenChildInstanceRequest request = requestDto.toSdk();
                request.setRegionId(regionId);
                DetachCenChildInstanceResponse response = this.acsClientStub.request(client, request);
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


    private DescribeCensResponse retrieveCen(IAcsClient client) throws AliCloudException, PluginException {

        DescribeCensRequest request = new DescribeCensRequest();
        DescribeCensResponse response;
        response = this.acsClientStub.request(client, request);
        return response;
    }

    private DescribeCenAttachedChildInstancesResponse retrieveCenAttachedChildInstance(IAcsClient client, String cenId) throws PluginException, AliCloudException {

        if (StringUtils.isEmpty(cenId)) {
            throw new PluginException("The CenId cannot be empty or null.");
        }

        DescribeCenAttachedChildInstancesRequest retrieveAttachedChildInstanceRequest = new DescribeCenAttachedChildInstancesRequest();
        retrieveAttachedChildInstanceRequest.setCenId(cenId);

        return this.acsClientStub.request(client, retrieveAttachedChildInstanceRequest);
    }

    private Boolean checkIfCenHasNoChildInstance(IAcsClient client, String cenId) {
        final DescribeCenAttachedChildInstancesResponse describeCenAttachedChildInstancesResponse = this.retrieveCenAttachedChildInstance(client, cenId);
        return describeCenAttachedChildInstancesResponse.getChildInstances().isEmpty();
    }

    private Boolean checkIfCenHasBeenDeleted(IAcsClient client, String cenId) {
        boolean ifCenDeleted = false;
        final DescribeCensResponse describeCensResponse = this.retrieveCen(client);
        final List<DescribeCensResponse.Cen> foundCenList = describeCensResponse.getCens().stream().filter(cen -> cenId.equals(cen.getCenId())).collect(Collectors.toList());

        if (foundCenList.isEmpty()) {
            ifCenDeleted = true;
        }
        return ifCenDeleted;
    }
}