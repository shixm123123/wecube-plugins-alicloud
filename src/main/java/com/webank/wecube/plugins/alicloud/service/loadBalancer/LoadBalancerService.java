package com.webank.wecube.plugins.alicloud.service.loadBalancer;

import com.webank.wecube.plugins.alicloud.dto.loadBalancer.CoreCreateLoadBalancerRequestDto;
import com.webank.wecube.plugins.alicloud.dto.loadBalancer.CoreCreateLoadBalancerResponseDto;
import com.webank.wecube.plugins.alicloud.dto.loadBalancer.CoreDeleteLoadBalancerRequestDto;
import com.webank.wecube.plugins.alicloud.dto.loadBalancer.CoreDeleteLoadBalancerResponseDto;
import com.webank.wecube.plugins.alicloud.dto.loadBalancer.backendServer.CoreAddBackendServerRequestDto;
import com.webank.wecube.plugins.alicloud.dto.loadBalancer.backendServer.CoreAddBackendServerResponseDto;
import com.webank.wecube.plugins.alicloud.dto.loadBalancer.backendServer.CoreRemoveBackendServerRequestDto;
import com.webank.wecube.plugins.alicloud.dto.loadBalancer.backendServer.CoreRemoveBackendServerResponseDto;

import java.util.List;

/**
 * @author howechen
 */
public interface LoadBalancerService {

    List<CoreCreateLoadBalancerResponseDto> createLoadBalancer(List<CoreCreateLoadBalancerRequestDto> coreCreateLoadBalancerRequestDtoList);

    List<CoreDeleteLoadBalancerResponseDto> deleteLoadBalancer(List<CoreDeleteLoadBalancerRequestDto> coreDeleteLoadBalancerRequestDtoList);

    List<CoreAddBackendServerResponseDto> addBackendServer(List<CoreAddBackendServerRequestDto> coreAddBackendServerRequestDtoList);

    List<CoreRemoveBackendServerResponseDto> removeBackendServer(List<CoreRemoveBackendServerRequestDto> coreRemoveBackendServerRequestDtoList);
}
