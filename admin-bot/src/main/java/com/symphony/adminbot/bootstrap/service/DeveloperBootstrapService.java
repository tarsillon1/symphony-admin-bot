package com.symphony.adminbot.bootstrap.service;

import com.symphony.adminbot.bootstrap.model.DeveloperBootstrapState;
import com.symphony.adminbot.bots.AdminBot;
import com.symphony.adminbot.commons.BotConstants;
import com.symphony.adminbot.config.BotConfig;
import com.symphony.adminbot.util.file.ExpiringFileLoaderCache;
import com.symphony.api.adminbot.model.Developer;
import com.symphony.api.adminbot.model.DeveloperBootstrapInfo;
import com.symphony.api.adminbot.model.DeveloperSignUpForm;
import com.symphony.api.clients.SymphonyClient;
import com.symphony.api.pod.client.ApiException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.BadRequestException;

/**
 * Created by nick.tarsillo on 7/5/17.
 */
public class DeveloperBootstrapService {
  private static final Logger LOG = LoggerFactory.getLogger(AdminBot.class);

  private ExpiringFileLoaderCache<Developer, DeveloperBootstrapState> partnerStateCache;

  private DeveloperUserService developerUserService;
  private DeveloperMessageService developerMessageService;
  private DeveloperCertService developerCertService;
  private DeveloperEmailService developerEmailService;

  public DeveloperBootstrapService(SymphonyClient symClient){
    partnerStateCache = new ExpiringFileLoaderCache<>(
        System.getProperty(BotConfig.JSON_DIR),
        (developer) -> developer.getEmail(),
        BotConstants.EXPIRE_TIME_DAYS,
        TimeUnit.DAYS,
        DeveloperBootstrapState.class);

    developerEmailService = new DeveloperEmailService();
    developerUserService = new DeveloperUserService(symClient.getUsersClient());
    developerMessageService = new DeveloperMessageService(symClient.getMessagesClient(),
        symClient.getStreamsClient());
    developerCertService = new DeveloperCertService(symClient.getSecurityClient(),
        symClient.getAttachmentsClient());
  }

  /**
   * Generate bot cert, register with pod, create bot user.
   * Generate app cert, register with pod, create app.
   * Update all team member states so they know certs were created.
   * Send symphony message containing the bootstrap package.
   * Package contains bot, app certs and info.
   * @param developer the bootstrap to base the bootstrap on
   * @return bootstrap info
   */
  public DeveloperBootstrapInfo bootstrapPartner(Developer developer) throws ApiException, ExecutionException {
    DeveloperBootstrapState developerState = partnerStateCache.get(developer);
    DeveloperSignUpForm signUpForm = developerState.getDeveloperSignUpForm();

    if(developerState.getBootstrapInfo() == null) {
      String password = developerState.getPassword();

      String botUsername = developerCertService.getDefaultBotUsername();
      botUsername = developerCertService.generateAndRegisterCert(botUsername, password);

      String appUsername = null;
      if(StringUtils.isNotBlank(signUpForm.getAppName())) {
        appUsername = developerCertService.generateAndRegisterCert(signUpForm.getAppName(), password);
      }

      developerUserService.createBot(signUpForm);

      DeveloperBootstrapInfo developerBootstrapInfo = new DeveloperBootstrapInfo();
      developerBootstrapInfo.setBotUsername(botUsername);
      developerBootstrapInfo.setAppId(appUsername);
      developerBootstrapInfo.setAppName(signUpForm.getAppName());
      developerBootstrapInfo.setBotEmail(signUpForm.getBotEmail());
      for(Developer teamMember: developerState.getTeamMembers()){
        DeveloperBootstrapState teamMemberState = partnerStateCache.get(teamMember);
        teamMemberState.setBootstrapInfo(developerBootstrapInfo);
      }
      developerState.setBootstrapInfo(developerBootstrapInfo);
    }

    developerCertService.uploadCerts(developerState);
    developerMessageService.sendBootstrapMessage(developerState);

    return developerState.getBootstrapInfo();
  }

  /**
   * Validates that all the fields in the sign up form are valid
   * Creates partner symphony user with random temp password.
   * Sends welcome email and message in symphony to partner.
   * @param signUpForm the partner sign up form
   */
  public void welcomePartner(DeveloperSignUpForm signUpForm) throws ApiException {
    if(!developerUserService.allPartnersDoNotExist(signUpForm)){
      throw new BadRequestException(BotConstants.USER_EXISTS);
    }

    if(developerUserService.botAndAppDoNotExist(signUpForm)){
      throw new BadRequestException(BotConstants.BOT_EXISTS);
    }

    Set<DeveloperBootstrapState> welcomeStates = developerUserService.getDeveloperSetupStates(signUpForm);
    for(DeveloperBootstrapState developerState : welcomeStates){
      partnerStateCache.put(developerState.getDeveloper(), developerState);

      developerUserService.createPartnerUser(developerState);
      developerEmailService.sendWelcomeEmail(developerState);
      developerMessageService.sendDirectionalMessage(developerState);
    }
  }

}
