<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "form">
        <div style="margin-bottom: 1.5rem;">
            <h3 class="fp-section-title">
                <span class="fp-section-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                </span>
                ${msg("loginAccountTitle")}
            </h3>
            <p class="fp-section-desc">${msg("login.subtitle")}</p>
        </div>

        <#if realm.password>
            <form id="kc-form-login" onsubmit="var b = document.getElementById('kc-login'); if (b) b.disabled = true; return true;" action="${url.loginAction}" method="post">
                <#if !usernameHidden??>
                    <div class="form-group">
                        <label for="username">
                            <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                        </label>
                        <input tabindex="1" id="username" name="username" value="${(login.username!'')}" type="text" autofocus autocomplete="off"
                            maxlength="25"
                            aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                            placeholder="<#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>"
                        />
                        <#if messagesPerField.existsError('username','password')>
                            <span class="alert alert-error" style="margin-top: 0.5rem; display: block;" aria-live="polite">
                                ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </#if>

                <div class="form-group">
                    <label for="password">${msg("password")}</label>
                    <div class="fp-password-wrap">
                        <input tabindex="2" id="password" name="password" type="password" autocomplete="off"
                            maxlength="128"
                            aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                            placeholder="${msg("password")}"
                        />
                        <button type="button" class="fp-password-toggle" data-target="password" aria-label="${msg("showPassword")}" tabindex="-1">
                            <svg class="fp-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
                            <svg class="fp-eye-off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:none"><path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c6.5 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/><path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3.5 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/><line x1="2" x2="22" y1="2" y2="22"/></svg>
                        </button>
                    </div>
                </div>

                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.5rem;">
                    <#if realm.rememberMe && !usernameHidden??>
                        <div class="checkbox">
                            <label>
                                <#if login.rememberMe??>
                                    <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" checked> ${msg("rememberMe")}
                                <#else>
                                    <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox"> ${msg("rememberMe")}
                                </#if>
                            </label>
                        </div>
                    </#if>
                    <#if realm.resetPasswordAllowed>
                        <div id="kc-form-options">
                            <a tabindex="5" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                        </div>
                    </#if>
                </div>

                <div id="kc-form-buttons">
                    <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                    <input tabindex="4" type="submit" value="${msg("doLogIn")}" id="kc-login" />
                </div>
            </form>
        </#if>

        <#if realm.password && social.providers??>
            <div class="kc-social-section">
                <div class="kc-social-links">
                    <#list social.providers as p>
                        <a id="social-${p.alias}" href="${p.loginUrl}">
                            <#if p.iconClasses?has_content><i class="${p.iconClasses}" aria-hidden="true"></i></#if>
                            <span class="kc-social-provider-name">${p.displayName!}</span>
                        </a>
                    </#list>
                </div>
            </div>
        </#if>

    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration">
                <span>${msg("noAccount")} <a tabindex="6" href="${url.registrationUrl}">${msg("doRegister")}</a></span>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
