<#import "template.ftl" as layout>
<#import "css.ftl" as css>
<#import "js.ftl" as js>

<#layout.layout(layout, "login", css, js) {

<div class="kc-login-wrapper">
  <div class="kc-login-panel">
    <div class="kc-brand">
      <div class="kc-brand-icon">QF</div>
      <div class="kc-brand-text">QueryForge</div>
    </div>

    <#if alert.error?exists>
      <div class="alert alert-error">${alert.error}</div>
    </#if>
    <#if alert.info?exists>
      <div class="alert alert-info">${alert.info}</div>
    </#if>

    <form id="kc-form-login" action="${url.loginActionUrl}" method="post">
      <input type="hidden" name="csrf_token" value="${csrfToken}"/>

      <div class="form-group">
        <label for="username">${msg("username")}</label>
        <input id="username" name="username" type="text" class="form-control" autocapitalize="off" autocomplete="username" required/>
      </div>

      <div class="form-group">
        <label for="password">${msg("password")}</label>
        <input id="password" name="password" type="password" class="form-control" autocomplete="current-password" required/>
      </div>

      <div class="form-group kc-checkbox">
        <input id="rememberMe" name="rememberMe" type="checkbox"/>
        <label for="rememberMe">${msg("rememberMe")}</label>
      </div>

      <div class="form-actions">
        <button id="kc-submit" class="btn btn-primary">${msg("signIn")}</button>
      </div>
    </form>

    <div class="kc-login-options">
      <a id="forgot-password" class="kc-link" href="${url.forgotPasswordUrl}">${msg("forgotPassword")}</a>
      <a id="register" class="kc-link" href="${url.registrationUrl}">${msg("registerLink")}</a>
    </div>
  </div>
</div>
} </#layout.layout>
