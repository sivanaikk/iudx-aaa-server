package iudx.aaa.server.policy;

import static iudx.aaa.server.policy.Constants.EMAIL_BODY;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.log4j.Logger;

/**
 * The Email Client.
 * <h1>Email Client</h1>
 * <p>
 * The Email Client assists the AAA server in managing email-related requests.
 * </p>
 *
 * @version 1.0
 * @since 2023-04-17
 */

public class EmailClient {

  private final String emailHostname;
  private final int emailPort;
  private final String emailUserName;
  private final String emailPassword;
  private final String senderEmail;
  private final String supportEmail;
  private final String publisherPanelURL;
  private final MailClient mailClient;

  private final Logger LOGGER = Logger.getLogger(EmailClient.class);
  public EmailClient(Vertx vertx, JsonObject config){
    this.emailHostname = config.getString("emailHostName");
    this.emailPort = config.getInteger("emailPort");
    this.emailUserName = config.getString("emailUserName");
    this.emailPassword = config.getString("emailPassword");
    this.senderEmail = config.getString("emailSender");
    this.supportEmail = config.getString("emailSupport");
    this.publisherPanelURL = config.getString("publisherPanelUrl");

    MailConfig mailConfig = new MailConfig();
    mailConfig.setStarttls(StartTLSOptions.REQUIRED);
    mailConfig.setLogin(LoginOption.REQUIRED);
    mailConfig.setConnectTimeout(5000);
    mailConfig.setIdleTimeout(10);
    mailConfig.setHostname(emailHostname);
    mailConfig.setPort(emailPort);
    mailConfig.setUsername(emailUserName);
    mailConfig.setPassword(emailPassword);
    mailConfig.setMaxMailsPerConnection(10000);
    mailConfig.setAllowRcptErrors(true);

    this.mailClient = MailClient.create(vertx, mailConfig);
  }

  /**
   *  This method is utilized to initiate the email sending process to
   *  the provider and delegate after the notification has been created.
   * @param emailInfo
   * @return
   */
  public Future<Void> sendEmail(EmailInfo emailInfo) {
    Promise<Void> promise = Promise.promise();

    UUID consumerId = emailInfo.getConsumerId();
    JsonObject consumer = emailInfo.getUserInfo(consumerId.toString());
    String consumerName = consumer.getJsonObject("name").getString("firstName")+" "
        +consumer.getJsonObject("name").getString("lastName");
    String consumerEmailId = consumer.getString("email");

    emailInfo.getItemDetails().values().forEach(resourceObj -> {
      UUID providerId = resourceObj.getOwnerId();
      JsonObject provider = emailInfo.getUserInfo(providerId.toString());
      String catId = resourceObj.getCatId();
      String providerEmailId = provider.getString("email");
      List<UUID> authDelegates = emailInfo.getProviderIdToAuthDelegateId()
          .get(providerId.toString());
      List<String> ccEmailIds = new ArrayList<>();
      ccEmailIds.add(supportEmail);

      // adding delegate email ids in ccEmailIds array list
       authDelegates.forEach(authDelegatesuuid ->{
        JsonObject delegate = emailInfo.getUserInfo(authDelegatesuuid.toString());
        ccEmailIds.add(delegate.getString("email"));
      } );

      String emailBody = EMAIL_BODY.replace("${CONSUMER_NAME}",consumerName)
          .replace("${CONSUMER_EMAIL}",consumerEmailId)
          .replace("${REQUESTED_CAT_ID}",catId)
          .replace("${PUBLISHER_PANEL_URL}",publisherPanelURL);

      //creating mail object
      MailMessage providerMail = new MailMessage();
      providerMail.setFrom(senderEmail);
      providerMail.setTo(providerEmailId);
      providerMail.setCc(ccEmailIds);
      providerMail.setText(emailBody);
      providerMail.setSubject("Request for policy for "+catId);

      mailClient.sendMail(providerMail,providerMailSuccessHandler->{
        if(providerMailSuccessHandler.succeeded()){
          LOGGER.info("email sent successfully "+providerMailSuccessHandler.result());
        }
        else{
          promise.fail(providerMailSuccessHandler.cause().getLocalizedMessage());
          LOGGER.info("Failed to send email "+providerMailSuccessHandler.cause().getLocalizedMessage());
        }
        });
    });
    return promise.future();
  }

}
