package com.itheima.aispringboot.common;

import lombok.Data;

@Data
public class Result<T> {
    private String code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(){
        Result<T> result=new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        return result;
}

//方法的重载
    public static <T> Result<T> ok(T data){
        Result<T> result=ok();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(){
        Result<T> result=new Result();
        result.setCode(ResultCode.ERROR.getCode());
        result.setMsg(ResultCode.ERROR.getMsg());
        return result;
    }

    public static <T> Result<T> error(String Code,String msg,T data){
        Result<T> result=error();
        result.setCode(Code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}
