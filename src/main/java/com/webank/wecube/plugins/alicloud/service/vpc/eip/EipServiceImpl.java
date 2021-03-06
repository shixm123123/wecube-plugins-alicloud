package com.webank.wecube.plugins.alicloud.service.vpc.eip;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.vpc.model.v20160428.*;
import com.webank.wecube.plugins.alicloud.common.PluginException;
import com.webank.wecube.plugins.alicloud.dto.CoreResponseDto;
import com.webank.wecube.plugins.alicloud.dto.IdentityParamDto;
import com.webank.wecube.plugins.alicloud.dto.cloudParam.CloudParamDto;
import com.webank.wecube.plugins.alicloud.dto.vpc.eip.*;
import com.webank.wecube.plugins.alicloud.support.AcsClientStub;
import com.webank.wecube.plugins.alicloud.support.AliCloudException;
import com.webank.wecube.plugins.alicloud.support.DtoValidator;
import com.webank.wecube.plugins.alicloud.support.timer.PluginTimer;
import com.webank.wecube.plugins.alicloud.support.timer.PluginTimerTask;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author howechen
 */
@Service
public class EipServiceImpl implements EipService {

    private static final Logger logger = LoggerFactory.getLogger(EipService.class);

    private final AcsClientStub acsClientStub;
    private final DtoValidator dtoValidator;

    @Autowired
    public EipServiceImpl(AcsClientStub acsClientStub, DtoValidator dtoValidator) {
        this.acsClientStub = acsClientStub;
        this.dtoValidator = dtoValidator;
    }

    @Override
    public List<CoreAllocateEipResponseDto> allocateEipAddress(List<CoreAllocateEipRequestDto> requestDtoList) {
        List<CoreAllocateEipResponseDto> resultList = new ArrayList<>();
        for (CoreAllocateEipRequestDto requestDto : requestDtoList) {
            CoreAllocateEipResponseDto result = new CoreAllocateEipResponseDto();
            try {
                this.dtoValidator.validate(requestDto);

                logger.info("Allocating EIP address: {}", requestDto.toString());

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();

                if (StringUtils.isNotEmpty(requestDto.getAllocationId())) {
                    final DescribeEipAddressesResponse describeEipAddressesResponse = retrieveEipByAllocationId(client, regionId, requestDto.getAllocationId(), true);
                    if (!describeEipAddressesResponse.getEipAddresses().isEmpty()) {
                        final DescribeEipAddressesResponse.EipAddress eipAddress = describeEipAddressesResponse.getEipAddresses().get(0);
                        result = result.fromSdkCrossLineage(eipAddress);
                        result.setRequestId(describeEipAddressesResponse.getRequestId());
                        continue;
                    }
                }


                AllocateEipAddressRequest allocateEipAddressRequest = requestDto.toSdk();
                AllocateEipAddressResponse createEipResponse = this.acsClientStub.request(client, allocateEipAddressRequest);

                // if cbpName is empty, create CBP
                String cbpId = StringUtils.EMPTY;
                if (!StringUtils.isEmpty(requestDto.getName())) {
                    DescribeCommonBandwidthPackagesRequest queryCBPRequest = new DescribeCommonBandwidthPackagesRequest();
                    queryCBPRequest.setName(requestDto.getName());
                    cbpId = queryCBP(client, queryCBPRequest, regionId);
                }

                if (StringUtils.isEmpty(cbpId)) {
                    final CreateCommonBandwidthPackageRequest createCommonBandwidthPackageRequest = requestDto.toSdkCrossLineage(CreateCommonBandwidthPackageRequest.class);
                    createCommonBandwidthPackageRequest.setName(requestDto.getName());
                    cbpId = createCBP(client, createCommonBandwidthPackageRequest, regionId);
                }

                final String allocationId = createEipResponse.getAllocationId();
                addEipToCBP(client, cbpId, allocationId);

                result = result.fromSdk(createEipResponse);
                result.setCbpId(cbpId);
                result.setCbpName(requestDto.getName());

            } catch (PluginException | AliCloudException ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setErrorMessage(ex.getMessage());
            } catch (Exception ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setUnhandledErrorMessage(ex.getMessage());
            } finally {
                result.setGuid(requestDto.getGuid());
                result.setCallbackParameter(requestDto.getCallbackParameter());
                logger.info("Result: {}", result.toString());
                resultList.add(result);
            }

        }
        return resultList;
    }


    @Override
    public List<CoreReleaseEipResponseDto> releaseEipAddress(List<CoreReleaseEipRequestDto> requestDtoList) {
        List<CoreReleaseEipResponseDto> resultList = new ArrayList<>();
        for (CoreReleaseEipRequestDto requestDto : requestDtoList) {
            CoreReleaseEipResponseDto result = new CoreReleaseEipResponseDto();
            try {
                this.dtoValidator.validate(requestDto);

                logger.info("Releasing EIP address: {}", requestDto.toString());

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();

                final DescribeEipAddressesResponse describeEipAddressesResponse = this.retrieveEipByAllocationId(client, regionId, requestDto.getAllocationId(), true);
                if (describeEipAddressesResponse.getEipAddresses().isEmpty()) {
                    result.setRequestId(describeEipAddressesResponse.getRequestId());
                    logger.info("The Eip address doesn't exist or has been released already.");
                    continue;
                }

                // remove eip from cbp
                String foundCBPId;
                if (!StringUtils.isEmpty(requestDto.getName())) {
                    DescribeCommonBandwidthPackagesRequest queryCBP = new DescribeCommonBandwidthPackagesRequest();
                    queryCBP.setName(requestDto.getName());
                    foundCBPId = queryCBP(client, queryCBP, regionId);
                } else {
                    foundCBPId = queryCBPByEip(client, regionId, requestDto.getAllocationId());
                }

                if (!StringUtils.isEmpty(foundCBPId)) {
                    removeFromCBP(client, requestDto.getAllocationId(), regionId, foundCBPId);
                }

                // release eip
                ReleaseEipAddressRequest request = requestDto.toSdk();
                ReleaseEipAddressResponse response = this.acsClientStub.request(client, request, regionId);
                result = result.fromSdk(response);

            } catch (PluginException | AliCloudException ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setErrorMessage(ex.getMessage());
            } catch (Exception ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setUnhandledErrorMessage(ex.getMessage());
            } finally {
                result.setGuid(requestDto.getGuid());
                result.setCallbackParameter(requestDto.getCallbackParameter());
                logger.info("Result: {}", result.toString());
                resultList.add(result);
            }

        }
        return resultList;
    }

    private String queryCBPByEip(IAcsClient client, String regionId, String allocationId) throws PluginException, AliCloudException {
        DescribeEipAddressesRequest request = new DescribeEipAddressesRequest();
        request.setAllocationId(allocationId);

        final DescribeEipAddressesResponse response = acsClientStub.request(client, request, regionId);
        if (response.getEipAddresses().isEmpty()) {
            return StringUtils.EMPTY;
        } else {
            return response.getEipAddresses().get(0).getBandwidthPackageId();
        }

    }

    private void removeFromCBP(IAcsClient client, String ipInstanceId, String regionId, String foundCBPId) throws AliCloudException {
        RemoveCommonBandwidthPackageIpRequest removeCommonBandwidthPackageIpRequest = new RemoveCommonBandwidthPackageIpRequest();
        removeCommonBandwidthPackageIpRequest.setBandwidthPackageId(foundCBPId);
        removeCommonBandwidthPackageIpRequest.setIpInstanceId(ipInstanceId);

        acsClientStub.request(client, removeCommonBandwidthPackageIpRequest, regionId);
    }

    @Override
    public void releaseEipAddress(IAcsClient client, String regionId, List<String> eipAllocationId) throws PluginException, AliCloudException {
        for (String allocationId : eipAllocationId) {

            logger.info("Releasing EIP address...");

            ReleaseEipAddressRequest request = new ReleaseEipAddressRequest();
            request.setAllocationId(allocationId);
            this.acsClientStub.request(client, request, regionId);
        }
    }

    @Override
    public void unAssociateEipAddress(IAcsClient client, String regionId, List<String> eipAllocationId, String instanceId, String instanceType) throws PluginException, AliCloudException {
        for (String allocationId : eipAllocationId) {

            logger.info("Un-associating EIP address with the instance...");

            UnassociateEipAddressRequest request = new UnassociateEipAddressRequest();
            request.setAllocationId(allocationId);
            request.setInstanceId(instanceId);
            request.setInstanceType(instanceType);
            this.acsClientStub.request(client, request, regionId);
        }
    }

    @Override
    public List<CoreAssociateEipResponseDto> associateEipAddress(List<CoreAssociateEipRequestDto> requestDtoList) {
        List<CoreAssociateEipResponseDto> resultList = new ArrayList<>();
        for (CoreAssociateEipRequestDto requestDto : requestDtoList) {
            CoreAssociateEipResponseDto result = new CoreAssociateEipResponseDto();
            try {
                this.dtoValidator.validate(requestDto);

                logger.info("Associating EIP address: {}", requestDto.toString());

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();

                AssociateEipAddressRequest request = requestDto.toSdk();
                AssociateEipAddressResponse response = this.acsClientStub.request(client, request, regionId);

                // wait till the eip is not in associating status
                Function<?, Boolean> func = o -> ifEipNotInStatus(client, regionId, requestDto.getAllocationId(), EipStatus.Associating);
                PluginTimer.runTask(new PluginTimerTask(func));

                result = result.fromSdk(response);

            } catch (PluginException | AliCloudException ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setErrorMessage(ex.getMessage());
            } catch (Exception ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setUnhandledErrorMessage(ex.getMessage());
            } finally {
                result.setGuid(requestDto.getGuid());
                result.setCallbackParameter(requestDto.getCallbackParameter());
                logger.info("Result: {}", result.toString());
                resultList.add(result);
            }

        }
        return resultList;
    }

    @Override
    public List<CoreUnAssociateEipResponseDto> unAssociateEipAddress(List<CoreUnAssociateEipRequestDto> requestDtoList) {
        List<CoreUnAssociateEipResponseDto> resultList = new ArrayList<>();
        for (CoreUnAssociateEipRequestDto requestDto : requestDtoList) {
            CoreUnAssociateEipResponseDto result = new CoreUnAssociateEipResponseDto();
            try {
                this.dtoValidator.validate(requestDto);

                logger.info("Un-associating EIP address: {}", requestDto.toString());

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final String regionId = cloudParamDto.getRegionId();

                UnassociateEipAddressRequest request = requestDto.toSdk();
                UnassociateEipAddressResponse response = this.acsClientStub.request(client, request, regionId);

                // wait till the eip is not in un-associating status
                Function<?, Boolean> func = o -> ifEipNotInStatus(client, regionId, requestDto.getAllocationId(), EipStatus.Unassociating);
                PluginTimer.runTask(new PluginTimerTask(func));

                result = result.fromSdk(response);
            } catch (PluginException | AliCloudException ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setErrorMessage(ex.getMessage());
            } catch (Exception ex) {
                result.setErrorCode(CoreResponseDto.STATUS_ERROR);
                result.setUnhandledErrorMessage(ex.getMessage());
            } finally {
                result.setGuid(requestDto.getGuid());
                result.setCallbackParameter(requestDto.getCallbackParameter());
                logger.info("Result: {}", result.toString());
                resultList.add(result);
            }

        }
        return resultList;
    }

    @Override
    public boolean ifEipIsAvailable(IAcsClient client, String regionId, String associatedInstanceType, String associatedInstanceId) throws PluginException, AliCloudException {

        if (!EnumUtils.isValidEnumIgnoreCase(AssociatedInstanceType.class, associatedInstanceType)) {
            throw new PluginException("Invalid associatedInstanceType.");
        }

        if (StringUtils.isAnyEmpty(regionId, associatedInstanceId)) {
            throw new PluginException("Either regionId or associatedInstanceId cannot be null or empty.");
        }

        logger.info("Retrieving if the given resource have no Eip bound.");

        DescribeEipAddressesRequest request = new DescribeEipAddressesRequest();
        request.setAssociatedInstanceType(associatedInstanceType);
        request.setAssociatedInstanceId(associatedInstanceId);

        DescribeEipAddressesResponse response;
        response = this.acsClientStub.request(client, request, regionId);
        return response.getTotalCount().equals(0);
    }

    @Override
    public void bindIpToInstance(IAcsClient client, String regionId, String instanceId, AssociatedInstanceType instanceType, String... ipAddress) throws PluginException, AliCloudException {
        for (String ip : ipAddress) {

            final DescribeEipAddressesResponse.EipAddress eipAddress = queryEipByAddress(client, regionId, ip);
            final String allocationId = eipAddress.getAllocationId();

            if (StringUtils.isNotEmpty(eipAddress.getInstanceId())) {
                if (StringUtils.equals(eipAddress.getInstanceId(), instanceId)) {
                    logger.info("The ip address: {} has already bound to that instance: {}", ipAddress, instanceId);
                    continue;
                } else {
                    throw new PluginException("That EIP has already bond to another instance");
                }
            }

            logger.info("Ip address: {} hasn't bound to the instance: {}, will create an association.", ip, instanceId);

            AssociateEipAddressRequest request = new AssociateEipAddressRequest();
            request.setAllocationId(allocationId);
            request.setInstanceId(instanceId);
            request.setInstanceType(instanceType.toString());

            logger.info("Associating EIP: {} to the instance: {}", allocationId, instanceId);

            acsClientStub.request(client, request, regionId);

            // wait till the eip is not in associating status
            Function<?, Boolean> func = o -> ifEipNotInStatus(client, regionId, allocationId, EipStatus.Associating);
            PluginTimer.runTask(new PluginTimerTask(func));
        }

    }

    @Override
    public void unbindIpFromInstance(IAcsClient client, String regionId, String instanceId, AssociatedInstanceType instanceType, String... ipAddress) throws PluginException, AliCloudException {
        for (String ip : ipAddress) {
            final DescribeEipAddressesResponse.EipAddress eipAddress = queryEipByAddress(client, regionId, ip);
            final String allocationId = eipAddress.getAllocationId();

            if (StringUtils.isEmpty(eipAddress.getInstanceId())) {
                continue;
            } else {
                if (!StringUtils.equals(eipAddress.getInstanceId(), instanceId)) {
                    throw new PluginException("That EIP has already bond to another instance");
                }
            }

            UnassociateEipAddressRequest request = new UnassociateEipAddressRequest();
            request.setAllocationId(allocationId);
            request.setInstanceId(instanceId);
            request.setInstanceType(instanceType.toString());

            logger.info("Unbind EIP: {} from isntance: {}", allocationId, instanceId);

            acsClientStub.request(client, request, regionId);

            // wait till the eip not in un-associating status
            Function<?, Boolean> func = o -> ifEipNotInStatus(client, regionId, allocationId, EipStatus.Unassociating);
            PluginTimer.runTask(new PluginTimerTask(func));
        }
    }

    private DescribeEipAddressesResponse retrieveEipByAllocationId(IAcsClient client, String regionId, String allocationId, boolean tolerateNotFound) {

        logger.info("Retrieving EIP info...");

        DescribeEipAddressesRequest request = new DescribeEipAddressesRequest();
        request.setAllocationId(allocationId);

        final DescribeEipAddressesResponse response = acsClientStub.request(client, request, regionId);

        if (!tolerateNotFound) {
            if (response.getEipAddresses().isEmpty()) {
                throw new PluginException(String.format("Cannot find EIP by given allocation Id: [%s]", allocationId));
            }
        }
        return response;
    }

    private void addEipToCBP(IAcsClient client, String cbpId, String allocationId) throws AliCloudException {
        AddCommonBandwidthPackageIpRequest request = new AddCommonBandwidthPackageIpRequest();
        request.setBandwidthPackageId(cbpId);
        request.setIpInstanceId(allocationId);

        acsClientStub.request(client, request);
    }

    private String queryCBP(IAcsClient client, DescribeCommonBandwidthPackagesRequest queryCBPRequest, String regionId) throws PluginException, AliCloudException {
        final DescribeCommonBandwidthPackagesResponse response = acsClientStub.request(client, queryCBPRequest, regionId);
        if (response.getCommonBandwidthPackages().isEmpty()) {
            return StringUtils.EMPTY;
        }

        return response.getCommonBandwidthPackages().get(0).getBandwidthPackageId();

    }

    private String createCBP(IAcsClient client, CreateCommonBandwidthPackageRequest createCommonBandwidthPackageRequest, String regionId) throws AliCloudException {

        final CreateCommonBandwidthPackageResponse response = acsClientStub.request(client, createCommonBandwidthPackageRequest, regionId);
        return response.getBandwidthPackageId();

    }

    private DescribeEipAddressesResponse.EipAddress queryEipByAddress(IAcsClient client, String regionId, String ipAddress) throws PluginException, AliCloudException {
        if (StringUtils.isEmpty(ipAddress)) {
            throw new PluginException("ipAddress cannot be empty or null while query EIP");
        }

        DescribeEipAddressesRequest queryEipRequest = new DescribeEipAddressesRequest();
        queryEipRequest.setEipAddress(ipAddress);

        final DescribeEipAddressesResponse response = acsClientStub.request(client, queryEipRequest, regionId);
        if (response.getEipAddresses().isEmpty()) {
            throw new PluginException(String.format("Cannot find EIP by given ip address: [%s]", ipAddress));
        }

        return response.getEipAddresses().get(0);
    }

    private boolean ifEipInStatus(IAcsClient client, String regionId, String allocationId, EipStatus status) throws AliCloudException {
        final DescribeEipAddressesResponse response = retrieveEipByAllocationId(client, regionId, allocationId, false);

        return StringUtils.equals(status.toString(), response.getEipAddresses().get(0).getStatus());
    }

    private boolean ifEipNotInStatus(IAcsClient client, String regionId, String allocationId, EipStatus status) throws AliCloudException {
        final DescribeEipAddressesResponse response = retrieveEipByAllocationId(client, regionId, allocationId, false);

        return !StringUtils.equals(status.toString(), response.getEipAddresses().get(0).getStatus());
    }


}
