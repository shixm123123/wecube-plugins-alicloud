package com.webank.wecube.plugins.alicloud.service.vpc.routeTable;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.vpc.model.v20160428.*;
import com.webank.wecube.plugins.alicloud.common.PluginException;
import com.webank.wecube.plugins.alicloud.dto.CloudParamDto;
import com.webank.wecube.plugins.alicloud.dto.CoreResponseDto;
import com.webank.wecube.plugins.alicloud.dto.IdentityParamDto;
import com.webank.wecube.plugins.alicloud.dto.vpc.routeTable.*;
import com.webank.wecube.plugins.alicloud.dto.vpc.routeTable.routeEntry.CoreCreateRouteEntryRequestDto;
import com.webank.wecube.plugins.alicloud.dto.vpc.routeTable.routeEntry.CoreCreateRouteEntryResponseDto;
import com.webank.wecube.plugins.alicloud.dto.vpc.routeTable.routeEntry.CoreDeleteRouteEntryRequestDto;
import com.webank.wecube.plugins.alicloud.dto.vpc.routeTable.routeEntry.CoreDeleteRouteEntryResponseDto;
import com.webank.wecube.plugins.alicloud.support.AcsClientStub;
import com.webank.wecube.plugins.alicloud.support.AliCloudConstant;
import com.webank.wecube.plugins.alicloud.support.AliCloudException;
import com.webank.wecube.plugins.alicloud.support.DtoValidator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author howechen
 */
@Service
public class RouteTableServiceImpl implements RouteTableService {
    private static Logger logger = LoggerFactory.getLogger(RouteTableService.class);
    private AcsClientStub acsClientStub;
    private DtoValidator dtoValidator;


    @Autowired
    public RouteTableServiceImpl(AcsClientStub acsClientStub, DtoValidator dtoValidator) {
        this.acsClientStub = acsClientStub;
        this.dtoValidator = dtoValidator;
    }

    @Override
    public List<CoreCreateRouteTableResponseDto> createRouteTable(List<CoreCreateRouteTableRequestDto> requestDtoList) throws PluginException {
        List<CoreCreateRouteTableResponseDto> resultList = new ArrayList<>();
        for (CoreCreateRouteTableRequestDto requestDto : requestDtoList) {

            CoreCreateRouteTableResponseDto result = new CoreCreateRouteTableResponseDto();

            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final String regionId = cloudParamDto.getRegionId();
                final String routeTableId = requestDto.getRouteTableId();
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);


                // if route table id exists, retrieve the info
                if (StringUtils.isNotEmpty(routeTableId)) {
                    final DescribeRouteTablesResponse retrieveRouteTableResponse = this.retrieveRouteTable(client, regionId, requestDto.getRouteTableId());
                    if (retrieveRouteTableResponse.getTotalCount() == 1) {
                        final DescribeRouteTablesResponse.RouteTable foundRouteTable = retrieveRouteTableResponse.getRouteTables().get(0);
                        result = result.fromSdkCrossLineage(foundRouteTable);
                        result.setRequestId(retrieveRouteTableResponse.getRequestId());
                        continue;
                    }

                }

                // create the route table

                if (StringUtils.isEmpty(requestDto.getVpcId())) {
                    String msg = "The vpcId cannot be null or empty.";
                    logger.info(msg);
                    throw new PluginException(msg);
                }


                final CreateRouteTableRequest request = requestDto.toSdk();
                request.setRegionId(regionId);


                CreateRouteTableResponse response;
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
    public CreateRouteTableResponse createRouteTable(IAcsClient client, CreateRouteTableRequest createRouteTableRequest) throws PluginException, AliCloudException {
        return this.acsClientStub.request(client, createRouteTableRequest);
    }


    @Override
    public DescribeRouteTablesResponse retrieveRouteTable(IAcsClient client, String regionId, String routeTableId) throws PluginException, AliCloudException {
        if (StringUtils.isEmpty(regionId)) {
            String msg = "The regionId cannot be null or empty.";
            logger.error(msg);
            throw new PluginException(msg);
        }

        logger.info(String.format("Retrieving route table info, region ID: [%s], route table ID: [%s]", regionId, routeTableId));

        DescribeRouteTablesRequest request = new DescribeRouteTablesRequest();
        request.setRegionId(regionId);
        request.setRouteTableId(routeTableId);

        DescribeRouteTablesResponse response;
        response = this.acsClientStub.request(client, request);

        return response;
    }

    @Override
    public List<CoreDeleteRouteTableResponseDto> deleteRouteTable(List<CoreDeleteRouteTableRequestDto> requestDtoList) throws PluginException {

        List<CoreDeleteRouteTableResponseDto> resultList = new ArrayList<>();

        for (CoreDeleteRouteTableRequestDto requestDto : requestDtoList) {

            CoreDeleteRouteTableResponseDto result = new CoreDeleteRouteTableResponseDto();

            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final String routeTableId = requestDto.getRouteTableId();
                final String regionId = cloudParamDto.getRegionId();
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);

                if (StringUtils.isAnyEmpty(regionId, routeTableId)) {
                    String msg = "The regionId or route table ID cannot be null";
                    logger.error(msg);
                    throw new PluginException(msg);
                }


                logger.info("Deleting route table, route table ID: [{}], route table region:[{}]", routeTableId, regionId);


                // check if route table already deleted
                final DescribeRouteTablesResponse retrieveRouteTableResponse = this.retrieveRouteTable(client, regionId, routeTableId);
                if (0 == retrieveRouteTableResponse.getTotalCount()) {
                    result.setRequestId(retrieveRouteTableResponse.getRequestId());
                    logger.info("The route table has already been deleted.");
                    continue;
                }

                final DescribeRouteTablesResponse.RouteTable foundRouteTable = retrieveRouteTableResponse.getRouteTables().get(0);

                // do not handle the system route table
                if (StringUtils.equals(AliCloudConstant.ROUTE_TABLE_TYPE_SYSTEM, foundRouteTable.getRouteTableType())) {
                    continue;
                }

                // un-associate all related VSwitches
                if (!foundRouteTable.getVSwitchIds().isEmpty()) {
                    for (String vSwitchId : foundRouteTable.getVSwitchIds()) {
                        UnassociateRouteTableRequest unassociateRouteTableRequest = new UnassociateRouteTableRequest();
                        unassociateRouteTableRequest.setRegionId(regionId);
                        unassociateRouteTableRequest.setRouteTableId(foundRouteTable.getRouteTableId());
                        unassociateRouteTableRequest.setVSwitchId(vSwitchId);
                        this.unAssociateRouteTable(client, unassociateRouteTableRequest);
                    }
                }

                // delete route table
                DeleteRouteTableRequest deleteRouteTableRequest = requestDto.toSdk();
                deleteRouteTableRequest.setRegionId(regionId);
                final DeleteRouteTableResponse response = this.acsClientStub.request(client, deleteRouteTableRequest);
                result = result.fromSdk(response);


                // re-check if route table has already been deleted
                if (0 != this.retrieveRouteTable(client, regionId, routeTableId).getTotalCount()) {
                    String msg = String.format("The route table: [%s] from region: [%s] hasn't been deleted", routeTableId, regionId);
                    logger.error(msg);
                    throw new PluginException(msg);
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

    @Override
    public DeleteRouteTableResponse deleteRouteTable(IAcsClient client, DeleteRouteTableRequest deleteRouteTableRequest) {
        return this.acsClientStub.request(client, deleteRouteTableRequest);
    }

    @Override
    public List<CoreAssociateRouteTableResponseDto> associateRouteTable(List<CoreAssociateRouteTableRequestDto> requestDtoList) throws PluginException {

        List<CoreAssociateRouteTableResponseDto> resultList = new ArrayList<>();

        for (CoreAssociateRouteTableRequestDto requestDto : requestDtoList) {

            CoreAssociateRouteTableResponseDto result = new CoreAssociateRouteTableResponseDto();

            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final String regionId = cloudParamDto.getRegionId();
                final String routeTableId = requestDto.getRouteTableId();
                final String vSwitchId = requestDto.getVSwitchId();

                if (StringUtils.isAnyEmpty(regionId, routeTableId, vSwitchId)) {
                    String msg = "Either regionId, routeTableId, vSwitchID cannot be null or empty.";
                    logger.error(msg);
                    throw new PluginException(msg);
                }

                logger.info(String.format("Associating route table: [%s] with VSwitch: [%s]", requestDto.getRouteTableId(), requestDto.getVSwitchId()));

                AssociateRouteTableRequest associateRouteTableRequest = requestDto.toSdk();
                associateRouteTableRequest.setRegionId(regionId);

                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);
                final AssociateRouteTableResponse response = this.acsClientStub.request(client, associateRouteTableRequest);
                result = result.fromSdk(response);

                logger.info("Route table and VSwitch successfully un-associated.");

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
    public void associateRouteTable(IAcsClient client, AssociateRouteTableRequest request) throws PluginException, AliCloudException {

        if (StringUtils.isAnyEmpty(request.getRegionId(), request.getRouteTableId(), request.getVSwitchId())) {
            String msg = "Either the region ID, route table ID or VSwitchID cannot be null or empty while associating route table with VSwitch";
            logger.error(msg);
            throw new PluginException(msg);
        }
        logger.info(String.format("Associating route table: [%s] with VSwitch: [%s]", request.getRouteTableId(), request.getVSwitchId()));

        this.acsClientStub.request(client, request);
        logger.info("Route table and VSwitch successfully associated.");
    }

    @Override
    public UnassociateRouteTableResponse unAssociateRouteTable(IAcsClient client, UnassociateRouteTableRequest unassociateRouteTableRequest) throws PluginException, AliCloudException {
        if (StringUtils.isAnyEmpty(unassociateRouteTableRequest.getRegionId(), unassociateRouteTableRequest.getRouteTableId(), unassociateRouteTableRequest.getVSwitchId())) {
            String msg = "Either the region ID, route table ID or VSwitchID cannot be null or empty while associating route table with VSwitch";
            logger.error(msg);
            throw new PluginException(msg);
        }

        logger.info(String.format("Un-associating route table: [%s] with VSwitch: [%s]", unassociateRouteTableRequest.getRouteTableId(), unassociateRouteTableRequest.getVSwitchId()));
        UnassociateRouteTableResponse response;
        response = this.acsClientStub.request(client, unassociateRouteTableRequest);
        logger.info("Route table and VSwitch successfully un-associated.");
        return response;
    }

    @Override
    public boolean checkIfRouteTableAvailable(IAcsClient client, String regionId, String routeTableId) throws PluginException {
        logger.info("Retrieving route table status.");
        if (StringUtils.isEmpty(regionId)) {
            String msg = "The regionId cannot be null or empty.";
            logger.error(msg);
            throw new PluginException(msg);
        }

        DescribeRouteTablesRequest request = new DescribeRouteTablesRequest();
        request.setRegionId(regionId);
        request.setRouteTableId(routeTableId);

        DescribeRouteTablesResponse response;
        try {
            response = this.acsClientStub.request(client, request);
        } catch (AliCloudException ex) {
            throw new PluginException(ex.getMessage());
        }

        return StringUtils.equals(AliCloudConstant.RESOURCE_AVAILABLE_STATUS, response.getRouteTables().get(0).getStatus());
    }

    @Override
    public List<CoreCreateRouteEntryResponseDto> createRouteEntry(List<CoreCreateRouteEntryRequestDto> coreCreateRouteEntryRequestDtoList) {
        List<CoreCreateRouteEntryResponseDto> resultList = new ArrayList<>();
        for (CoreCreateRouteEntryRequestDto requestDto : coreCreateRouteEntryRequestDtoList) {

            CoreCreateRouteEntryResponseDto result = new CoreCreateRouteEntryResponseDto();
            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final String regionId = cloudParamDto.getRegionId();
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);

                CreateRouteEntryRequest request = requestDto.toSdk();
                request.setRegionId(regionId);
                CreateRouteEntryResponse response;

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
    public List<CoreDeleteRouteEntryResponseDto> deleteRouteEntry(List<CoreDeleteRouteEntryRequestDto> coreDeleteRouteEntryRequestDtoList) {
        List<CoreDeleteRouteEntryResponseDto> resultList = new ArrayList<>();
        for (CoreDeleteRouteEntryRequestDto requestDto : coreDeleteRouteEntryRequestDtoList) {

            CoreDeleteRouteEntryResponseDto result = new CoreDeleteRouteEntryResponseDto();

            try {

                dtoValidator.validate(requestDto);

                final IdentityParamDto identityParamDto = IdentityParamDto.convertFromString(requestDto.getIdentityParams());
                final CloudParamDto cloudParamDto = CloudParamDto.convertFromString(requestDto.getCloudParams());
                final String regionId = cloudParamDto.getRegionId();
                final IAcsClient client = this.acsClientStub.generateAcsClient(identityParamDto, cloudParamDto);

                DeleteRouteEntryRequest request = requestDto.toSdk();
                request.setRegionId(regionId);
                DeleteRouteEntryResponse response;

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


}