<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow=msg("emailVerificationCode.eyebrow")
    title=msg("emailVerificationCode.title")
    subtitle=msg("emailVerificationCode.subtitle")>
    <p>${msg("email.greeting")} <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>${msg("emailVerificationCode.body1")}</p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="code-block">
                <div class="code-frame">
                    <p class="code-eyebrow">${msg("email.codeEyebrow")}</p>
                    <span class="email-code">${code}</span>
                </div>
            </td>
        </tr>
    </table>
    <div class="info-box">
        <div class="info-card">
            <p><strong>${msg("email.expiryLabel")}</strong> &nbsp; ${msg("emailVerificationCode.expiryHint")}</p>
        </div>
    </div>
    <p style="margin-top:18px;">${msg("emailVerificationCode.footer")}</p>
</@layout.emailLayout>
