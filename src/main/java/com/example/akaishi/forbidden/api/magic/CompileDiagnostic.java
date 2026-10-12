package com.example.akaishi.forbidden.api.magic;

/** code 为稳定机器标识，detail 供调试与显示适配，slot=-1 表示整句。 */
public record CompileDiagnostic(Severity severity, String code, int slot, String detail) {
    public enum Severity { ERROR, INFO }
}
