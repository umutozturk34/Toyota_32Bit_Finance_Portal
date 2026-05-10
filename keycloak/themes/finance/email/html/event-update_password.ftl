<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow=msg("eventUpdatePassword.eyebrow")
    title=msg("eventUpdatePassword.title")
    subtitle=msg("eventUpdatePassword.subtitle")>
    <p>${msg("email.greeting")} <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>${kcSanitize(msg("eventUpdatePassword.body1"))?no_esc}</p>
    <div class="info-box">
        <div class="info-card">
            <p><strong>${msg("eventUpdatePassword.alertLabel")}</strong> &nbsp; ${msg("eventUpdatePassword.alertHint")}</p>
        </div>
    </div>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="cta-wrap" align="left">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" class="cta-table">
                    <tr>
                        <td class="cta-cell" align="center"><a href="${link}" class="email-cta">${msg("eventUpdatePassword.cta")} &nbsp;→</a></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <p style="margin-top:18px;">${msg("eventUpdatePassword.footer")}</p>
</@layout.emailLayout>
