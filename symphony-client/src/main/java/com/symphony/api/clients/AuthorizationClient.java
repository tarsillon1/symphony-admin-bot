/*
 * Copyright 2017 The Symphony Software Foundation
 *
 * Licensed to The Symphony Software Foundation (SSF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package com.symphony.api.clients;

import com.symphony.api.auth.api.AuthenticationApi;
import com.symphony.api.auth.client.ApiClient;
import com.symphony.api.auth.client.ApiException;
import com.symphony.api.auth.client.Configuration;
import com.symphony.api.clients.model.SymphonyAuth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.security.KeyStore;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

/**
 * Created by nick.tarsillo on 7/1/17.
 */
public class AuthorizationClient {
  private final Logger LOG = LoggerFactory.getLogger(AuthorizationClient.class);

  private final String sessionAuthUrl;
  private final String keyAuthUrl;

  private Client httpClient;

  public AuthorizationClient(String sessionAuthUrl, String keyAuthUrl){
    this.sessionAuthUrl = sessionAuthUrl;
    this.keyAuthUrl = keyAuthUrl;
  }

  public AuthorizationClient(SymphonyAuth symAuth) {
    this.sessionAuthUrl = symAuth.getSessionUrl();
    this.keyAuthUrl = symAuth.getKeyUrl();
    this.httpClient = symAuth.getClient();
  }


  public SymphonyAuth authenticate() throws ApiException {
      if(sessionAuthUrl == null || keyAuthUrl == null)
        throw new NullPointerException("Session URL or Keystore URL is null.");

      SymphonyAuth symAuth = new SymphonyAuth();
      symAuth.setKeyUrl(keyAuthUrl);
      symAuth.setSessionUrl(sessionAuthUrl);
      symAuth.setClient(httpClient);

      ApiClient authenticatorClient = Configuration.getDefaultApiClient();
      authenticatorClient.setHttpClient(httpClient);
      AuthenticationApi authenticationApi = new AuthenticationApi(authenticatorClient);

      authenticatorClient.setBasePath(sessionAuthUrl);
      symAuth.setSessionToken(authenticationApi.v1AuthenticatePost());
      LOG.debug("SessionToken: {} : {}", symAuth.getSessionToken().getName(), symAuth.getSessionToken().getToken());

      authenticatorClient.setBasePath(keyAuthUrl);
      symAuth.setKeyToken(authenticationApi.v1AuthenticatePost());
      LOG.debug("KeyToken: {} : {}", symAuth.getKeyToken().getName(), symAuth.getKeyToken().getToken());

      return symAuth;
  }

  /**
   * Create custom client with specific keystores.
   *
   * @param clientKeyStore     Client (BOT) keystore file
   * @param clientKeyStorePass Client (BOT) keystore password
   * @param trustStore         Truststore file
   * @param trustStorePass     Truststore password
   * @return Custom HttpClient
   * @throws Exception Generally IOExceptions thrown from instantiation.
   */
  public void setKeystores(String trustStore, String trustStorePass, String clientKeyStore,
      String clientKeyStorePass) throws Exception {
    KeyStore cks = KeyStore.getInstance("PKCS12");
    KeyStore tks = KeyStore.getInstance("JKS");

    loadKeyStore(cks, clientKeyStore, clientKeyStorePass);
    loadKeyStore(tks, trustStore, trustStorePass);

    httpClient = ClientBuilder.newBuilder()
        .keyStore(cks, clientKeyStorePass.toCharArray())
        .trustStore(tks)
        .hostnameVerifier((string, sll) -> true)
        .build();
  }

  /**
   * Internal keystore loader
   *
   * @param ks     Keystore object which defines the expected type (PKCS12, JKS)
   * @param ksFile Keystore file to process
   * @param ksPass Keystore password for file to process
   * @throws Exception Generally IOExceptions generated from file read
   */
  private static void loadKeyStore(KeyStore ks, String ksFile, String ksPass) throws Exception {
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(ksFile);
      ks.load(fis, ksPass.toCharArray());
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }
}
