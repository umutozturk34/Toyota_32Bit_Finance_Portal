<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName','lastName','email','username','password','password-confirm'); section>
    <#if section = "form">
        <div style="margin-bottom: 1.5rem;">
            <h3 class="fp-section-title">
                <span class="fp-section-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="20" y1="8" x2="20" y2="14"/><line x1="23" y1="11" x2="17" y2="11"/></svg>
                </span>
                ${msg("registerTitle")}
            </h3>
            <p class="fp-section-desc">${msg("register.subtitle")}</p>
        </div>

        <form id="kc-register-form" action="${url.registrationAction}" method="post">
            <div class="form-group">
                <label for="firstName">${msg("firstName")}</label>
                <input type="text" id="firstName" name="firstName" value="${(register.formData.firstName!'')}"
                    placeholder="${msg("firstName")}"
                    aria-invalid="<#if messagesPerField.existsError('firstName')>true</#if>" />
                <#if messagesPerField.existsError('firstName')>
                    <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('firstName'))?no_esc}</span>
                </#if>
            </div>

            <div class="form-group">
                <label for="lastName">${msg("lastName")}</label>
                <input type="text" id="lastName" name="lastName" value="${(register.formData.lastName!'')}"
                    placeholder="${msg("lastName")}"
                    aria-invalid="<#if messagesPerField.existsError('lastName')>true</#if>" />
                <#if messagesPerField.existsError('lastName')>
                    <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('lastName'))?no_esc}</span>
                </#if>
            </div>

            <div class="form-group">
                <label for="email">${msg("email")}</label>
                <input type="text" id="email" name="email" value="${(register.formData.email!'')}"
                    autocomplete="email" placeholder="${msg("email")}"
                    aria-invalid="<#if messagesPerField.existsError('email')>true</#if>" />
                <#if messagesPerField.existsError('email')>
                    <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('email'))?no_esc}</span>
                </#if>
            </div>

            <#if !realm.registrationEmailAsUsername>
                <div class="form-group">
                    <label for="username">${msg("username")}</label>
                    <input type="text" id="username" name="username" value="${(register.formData.username!'')}"
                        autocomplete="username" placeholder="${msg("username")}"
                        aria-invalid="<#if messagesPerField.existsError('username')>true</#if>" />
                    <#if messagesPerField.existsError('username')>
                        <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
                    </#if>
                </div>
            </#if>

            <#if passwordRequired??>
                <div class="form-group">
                    <label for="password">${msg("password")}</label>
                    <div class="fp-password-wrap">
                        <input type="password" id="password" name="password" autocomplete="new-password"
                            placeholder="${msg("password")}"
                            aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>" />
                        <button type="button" class="fp-password-toggle" data-target="password" aria-label="${msg("showPassword")}" tabindex="-1">
                            <svg class="fp-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
                            <svg class="fp-eye-off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:none"><path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c6.5 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/><path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3.5 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/><line x1="2" x2="22" y1="2" y2="22"/></svg>
                        </button>
                    </div>
                    <#if messagesPerField.existsError('password')>
                        <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
                    </#if>
                </div>

                <div class="form-group">
                    <label for="password-confirm">${msg("passwordConfirm")}</label>
                    <div class="fp-password-wrap">
                        <input type="password" id="password-confirm" name="password-confirm"
                            placeholder="${msg("passwordConfirm")}"
                            aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>" />
                        <button type="button" class="fp-password-toggle" data-target="password-confirm" aria-label="${msg("showPassword")}" tabindex="-1">
                            <svg class="fp-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
                            <svg class="fp-eye-off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:none"><path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c6.5 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/><path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3.5 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/><line x1="2" x2="22" y1="2" y2="22"/></svg>
                        </button>
                    </div>
                    <#if messagesPerField.existsError('password-confirm')>
                        <span class="alert alert-error" style="margin-top: 0.5rem; display: block;">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
                    </#if>
                </div>
            </#if>

            <#if recaptchaRequired??>
                <div class="form-group">
                    <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}"></div>
                </div>
            </#if>

            <input type="hidden" id="themePreferenceAttr" name="user.attributes.themePreference" value="DARK" />
            <input type="hidden" id="localeAttr" name="user.attributes.locale" value="${locale.currentLanguageTag!'en'}" />

            <div id="kc-form-buttons" style="margin-top: 1.5rem;">
                <input type="submit" value="${msg("doRegister")}" />
            </div>

            <div id="kc-registration" style="margin-top: 1.25rem; text-align: center;">
                <span>${msg("register.alreadyHaveAccount")} <a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a></span>
            </div>
        </form>
        <script>
          (function () {
            function getCookie(name) {
              var match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
              return match ? decodeURIComponent(match[1]) : null;
            }
            function readParams() {
              var hash = window.location.hash || '';
              if (hash.startsWith('#')) hash = hash.slice(1);
              try { return new URLSearchParams(window.location.search + '&' + hash); }
              catch (e) { return new URLSearchParams(window.location.search); }
            }
            function readTheme() {
              try {
                var p = readParams().get('themePreference');
                if (p) return p.toUpperCase();
              } catch (e) { /* noop */ }
              var c = (getCookie('finance-theme') || '').toLowerCase();
              if (c === 'light' || c === 'dark') return c.toUpperCase();
              try {
                var ls = (localStorage.getItem('finance-theme') || '').toLowerCase();
                if (ls === 'light' || ls === 'dark') return ls.toUpperCase();
              } catch (e) { /* noop */ }
              return null;
            }
            function readLocale() {
              try {
                var p = readParams().get('kc_locale');
                if (p === 'tr' || p === 'en') return p;
              } catch (e) { /* noop */ }
              var c = (getCookie('KEYCLOAK_LOCALE') || getCookie('finance-language') || '').toLowerCase();
              if (c === 'tr' || c === 'en') return c;
              try {
                var ls = (localStorage.getItem('finance-language') || '').toLowerCase();
                if (ls === 'tr' || ls === 'en') return ls;
              } catch (e) { /* noop */ }
              return null;
            }
            try {
              var theme = readTheme();
              if (theme) {
                var input = document.getElementById('themePreferenceAttr');
                if (input) input.value = theme;
              }
              var locale = readLocale();
              if (locale) {
                var localeInput = document.getElementById('localeAttr');
                if (localeInput) localeInput.value = locale;
              }
            } catch (e) { /* noop */ }
          })();
        </script>
    </#if>
</@layout.registrationLayout>
