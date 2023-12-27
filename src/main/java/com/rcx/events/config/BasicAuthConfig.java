package com.rcx.events.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.rcx.events.properties.SpringSecurityProperties;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@Order(1)
public class BasicAuthConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  SpringSecurityProperties properties;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.authorizeRequests().antMatchers("/actuator/health**").permitAll().and()
        .antMatcher("/actuator/**").authorizeRequests().antMatchers("/actuator/**").authenticated()
        .and().httpBasic().and().csrf().disable();
  }

  @Override
  protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.inMemoryAuthentication().withUser(properties.getName())
        .password(passwordEncoder().encode(properties.getPassword())).roles(properties.getRoles());
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

}
