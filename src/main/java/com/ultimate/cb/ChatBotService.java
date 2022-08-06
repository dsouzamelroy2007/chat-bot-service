package com.ultimate.cb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultimate.cb.repository.IntentReplyRepository;
import com.ultimate.cb.util.CustomJackson2RepositoryPopulatorFactoryBean;
import java.time.Duration;
import java.util.TimeZone;
import javax.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.repository.init.AbstractRepositoryPopulatorFactoryBean;
import org.springframework.data.repository.init.Jackson2RepositoryPopulatorFactoryBean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableAsync
@EnableSwagger2
@EnableHystrix
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
public class ChatBotService {

  public static void main(String[] args) {
    SpringApplication.run(ChatBotService.class, args);
  }

  @PostConstruct
  public void init(){
    // Setting Spring Boot SetTimeZone
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {

    return builder
            .setConnectTimeout(Duration.ofMillis(10000))
            .setReadTimeout(Duration.ofMillis(10000))
            .build();
  }

  @Bean("threadPoolTaskExecutor")
  public TaskExecutor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(100);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setThreadNamePrefix("Async-");
    return executor;
  }

  @Bean
  public AbstractRepositoryPopulatorFactoryBean repositoryPopulator(ObjectMapper objectMapper, IntentReplyRepository intentReplyRepository) {
    Jackson2RepositoryPopulatorFactoryBean factory = new CustomJackson2RepositoryPopulatorFactoryBean();
    intentReplyRepository.deleteAll();
    factory.setMapper(objectMapper);
    factory.setResources(new Resource[]{new ClassPathResource("intents.json")});
    return factory;
  }

  @Bean
  public Docket apis(){
    return new Docket(DocumentationType.SWAGGER_2)
                                  .select()
                                  .apis(RequestHandlerSelectors.basePackage("com.ultimate.cb.controller"))
                                  .paths(PathSelectors.any())
                                  .build()
                                  .pathMapping("")
                                  .apiInfo(getApiInfo());
  }

  private ApiInfo getApiInfo() {
    return new ApiInfo("Chat Bot Service",
                "Generate replies to customers based on the message intent",
                   "1.0","wwww.xyz.com",
                            new Contact("dev_team","","tech-support@.com"),
                    null,
                  null);
  }

}
