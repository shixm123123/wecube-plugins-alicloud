package com.webank.wecube.plugins.alicloud.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;

/**
 * @author howechen
 */
public class CoreResponseDto<E extends CoreResponseOutputDto> {
    public final static String STATUS_OK = "0";
    public final static String STATUS_ERROR = "1";

    @JsonProperty(value = "resultCode")
    private String resultCode;
    @JsonProperty(value = "resultMessage")
    private String resultMessage;
    @JsonProperty(value = "results")
    private Result<E> results;

    public static CoreResponseDto okay() {
        CoreResponseDto result = new CoreResponseDto();
        result.setResultCode(STATUS_OK);
        result.setResultMessage("Success");
        return result;
    }

    public static CoreResponseDto error(String errorMessage) {
        CoreResponseDto result = new CoreResponseDto();
        result.setResultCode(STATUS_ERROR);
        result.setResultMessage(errorMessage);
        return result;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public Result<E> getResults() {
        return results;
    }

    public void setResults(List<E> results) {
        this.results = new Result<>(results);
    }

    public CoreResponseDto<E> withData(List<E> data) {
        setResults(data);
        return this;
    }

    public CoreResponseDto<E> withErrorCheck(List<E> data) {
        final Optional<E> foundFirstErrorOpt = data.stream().filter(e -> STATUS_ERROR.equals(e.getErrorCode())).findFirst();
        if (foundFirstErrorOpt.isPresent()) {
            final String errorMessage = foundFirstErrorOpt.get().getErrorMessage();
            return error(errorMessage).withData(data);
        }
        return okay().withData(data);
    }

    static class Result<E extends CoreResponseOutputDto> {
        @JsonProperty(value = "outputs")
        private List<E> results;

        public Result() {
        }

        public Result(List<E> results) {
            this.results = results;
        }

        public List<E> getResults() {
            return results;
        }

        public void setResults(List<E> results) {
            this.results = results;
        }

    }


}
