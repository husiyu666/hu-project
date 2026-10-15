package com.husiyu.aipsy.exception;

import lombok.Getter;

import static com.husiyu.aipsy.common.ResultCode.BUSINESS_ERROR;

/**
 * 业务异常类
 * @author system
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final String message;
    private final Object data;

    public BusinessException(String message) {
        super(message);
        this.code = "BUSINESS_ERROR";
        this.message = message;
        this.data = null;
    }

}