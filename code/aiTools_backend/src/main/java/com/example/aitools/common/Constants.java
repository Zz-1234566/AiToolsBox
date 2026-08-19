package com.example.aitools.common;

public class Constants {

    private Constants() {}

    /** Token header name */
    public static final String TOKEN_HEADER = "Authorization";

    /** Token prefix */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Account prefix */
    public static final String ACCOUNT_PREFIX = "AIT";

    /** Account random digit count */
    public static final int ACCOUNT_DIGIT_COUNT = 8;

    /** User status: normal */
    public static final int USER_STATUS_NORMAL = 1;

    /** User status: disabled */
    public static final int USER_STATUS_DISABLED = 0;

    /** Logical delete: normal */
    public static final int DR_NORMAL = 0;

    /** Logical delete: deleted */
    public static final int DR_DELETED = 1;

    /** Default page size */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** Max page size */
    public static final int MAX_PAGE_SIZE = 100;
}
