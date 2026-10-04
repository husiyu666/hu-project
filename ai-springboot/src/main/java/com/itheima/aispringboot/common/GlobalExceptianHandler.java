package com.itheima.aispringboot.common;

import com.itheima.aispringboot.exception.BusinessException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;
@RestControllerAdvice
//Spring 提供的全局控制器增强注解，专门处理 @RestController 抛出的异常。
public class GlobalExceptianHandler {
//处理参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
  public Result<String> handlerException(MethodArgumentNotValidException e){
        //处理异常数据的处理
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(","));
        return Result.error(ResultCode.PARAM_ERROR.getCode(),ResultCode.PARAM_ERROR.getMsg(),message);
    }
    //处理业务校验异常
    @ExceptionHandler(BusinessException.class)
    public Result<?> handlerBusinessException(BusinessException e){
        if(e.getData()!=null){
            return  Result.error(e.getCode(),e.getMessage(),e.getData());
        }
        return  Result.error(e.getCode(),e.getMessage(),null);
    }
}
