package com.finance.backend.service;

public interface AssetExistenceChecker {

    boolean existsInApi(String code);
}
