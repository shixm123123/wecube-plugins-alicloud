package com.webank.wecube.plugins.alicloud.service.cen;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.cbn.model.v20170912.*;
import com.webank.wecube.plugins.alicloud.common.PluginException;
import com.webank.wecube.plugins.alicloud.dto.CloudParamDto;
import com.webank.wecube.plugins.alicloud.dto.CoreResponseDto;
import com.webank.wecube.plugins.alicloud.dto.IdentityParamDto;
import com.webank.wecube.plugins.alicloud.dto.cen.*;
import com.webank.wecube.plugins.alicloud.support.AcsClientStub;
import com.webank.wecube.plugins.alicloud.support.AliCloudException;
import com.webank.wecube.plugins.alicloud.support.DtoValidator;
import com.webank.wecube.plugins.alicloud.support.PluginSdkBridge;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.webank.wecube.plugins.alicloud.support.PluginConstant.COUNT_DOWN_TIME;

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
                requestDto.setRegionId(regionId);

                final String cenId = requestDto.getCenId();
                if (StringUtils.isNotEmpty(cenId)) {
                    final DescribeCensResponse.Cen foundCen = this.retrieveCen(client, cenId);
                    if (null != foundCen) {
                        result = PluginSdkBridge.fromSdk(foundCen, CoreCreateCenResponseDto.class);
                        continue;
                    }
                }

                logger.info("Creating Cen...");

                CreateCenRequest request = PluginSdkBridge.toSdk(requestDto, CreateCenRequest.class);
                CreateCenResponse response = this.acsClientStub.request(client, request);
                result = PluginSdkBridge.fromSdk(response, CoreCreateCenResponseDto.class);

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
                requestDto.setRegionId(regionId);

                final String cenId = requestDto.getCenId();
                final DescribeCensResponse.Cen foundCen;
                if (StringUtils.isNotEmpty(cenId)) {
                    foundCen = this.retrieveCen(client, cenId);
                    if (null == foundCen) {
                        throw new PluginException(String.format("Cannot find Cen by ID: %s", cenId));
                    }
                }

                // check if cen has child instances

                final DescribeCenAttachedChildInstancesResponse describeCenAttachedChildInstancesResponse = this.retrieveCenAttachedChildInstance(client, cenId);
                final List<DescribeCenAttachedChildInstancesResponse.ChildInstance> childInstances = describeCenAttachedChildInstancesResponse.getChildInstances();
                if (!childInstances.isEmpty()) {
                    detachAllChildInstances(client, cenId, childInstances);
                }

                logger.info("Deleting Cen...");

                CreateCenRequest request = PluginSdkBridge.toSdk(requestDto, CreateCenRequest.class);
                CreateCenResponse response = this.acsClientStub.request(client, request);
                result = PluginSdkBridge.fromSdk(response, CoreDeleteCenResponseDto.class);

                // TODO: need to optimize the timer
                // check if cen has already been deleted
                boolean hasDeleted = false;
                try {

                    for (int i = 0; i < COUNT_DOWN_TIME; i++) {
                        hasDeleted = this.checkIfCenHasBeenDeleted(client, cenId);
                        if (hasDeleted) {
                            logger.info("The Cen: [{}] has already been deleted.", cenId);
                            hasDeleted = true;
                            break;
                        }
                        TimeUnit.SECONDS.sleep(1);
                    }
                } catch (InterruptedException e) {
                    throw new PluginException(e.getMessage());
                }
                if (!hasDeleted) {
                    throw new PluginException("The Cen hasn't been deleted.");
                }

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

        // TODO: need to optimize the timer
        boolean ifAllChildInstancesHasBeenDeleted = false;
        try {
            for (int i = 0; i < COUNT_DOWN_TIME; i++) {
                ifAllChildInstancesHasBeenDeleted = this.checkIfCenHasNoChildInstance(client, cenId);
                if (ifAllChildInstancesHasBeenDeleted) {
                    logger.info("All child instances has been deleted.");
                    break;
                }
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            throw new PluginException(e.getMessage());
        }

        if (!ifAllChildInstancesHasBeenDeleted) {
            throw new PluginException("Not all child instances of Cen haven been deleted.");
        }
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
                requestDto.setRegionId(regionId);

                logger.info("Attaching Cen child instance...");

                AttachCenChildInstanceRequest request = PluginSdkBridge.toSdk(requestDto, AttachCenChildInstanceRequest.class);
                AttachCenChildInstanceResponse response = this.acsClientStub.request(client, request);
                result = PluginSdkBridge.fromSdk(response, CoreAttachCenChildResponseDto.class);

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
                requestDto.setRegionId(regionId);

                logger.info("Detaching Cen child instance...");

                DetachCenChildInstanceRequest request = PluginSdkBridge.toSdk(requestDto, DetachCenChildInstanceRequest.class);
                DetachCenChildInstanceResponse response = this.acsClientStub.request(client, request);
                result = PluginSdkBridge.fromSdk(response, CoreDetachCenChildResponseDto.class);

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


    private DescribeCensResponse.Cen retrieveCen(IAcsClient client, String cenId) throws AliCloudException, PluginException {

        logger.info("Retrieving Cen. CenId: [{}]", cenId);

        DescribeCensRequest request = new DescribeCensRequest();
        DescribeCensResponse response;
        response = this.acsClientStub.request(client, request);
        final List<DescribeCensResponse.Cen> foundCenList = response.getCens().stream().filter(cen -> cenId.equals(cen.getCenId())).collect(Collectors.toList());

        if (foundCenList.size() == 1) {
            return foundCenList.get(0);
        } else {
            return null;
        }
    }

    private DescribeCenAttachedChildInstancesResponse retrieveCenAttachedChildInstance(IAcsClient client, String cenId) throws PluginException, AliCloudException {

        if (StringUtils.isEmpty(cenId)) {
            throw new PluginException("The CenId cannot be empty or null.");
        }

        DescribeCenAttachedChildInstancesRequest retrieveAttachedChildInstanceRequest = new DescribeCenAttachedChildInstancesRequest();
        retrieveAttachedChildInstanceRequest.setCenId(cenId);

        return this.acsClientStub.request(client, retrieveAttachedChildInstanceRequest);
    }

    private boolean checkIfCenHasNoChildInstance(IAcsClient client, String cenId) {
        final DescribeCenAttachedChildInstancesResponse describeCenAttachedChildInstancesResponse = this.retrieveCenAttachedChildInstance(client, cenId);
        return describeCenAttachedChildInstancesResponse.getChildInstances().isEmpty();
    }

    private boolean checkIfCenHasBeenDeleted(IAcsClient client, String cenId) {
        boolean ifCenDeleted = false;
        final DescribeCensResponse.Cen cen = this.retrieveCen(client, cenId);
        if (null == cen) {
            ifCenDeleted = true;
        }
        return ifCenDeleted;
    }
}
