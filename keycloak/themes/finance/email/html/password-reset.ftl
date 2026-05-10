<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow=msg("passwordReset.eyebrow")
    title=msg("passwordReset.title")
    subtitle=msg("passwordReset.subtitle")>
    <p>${msg("email.greeting")} <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>${msg("passwordReset.body1")}</p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="cta-wrap" align="left">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" class="cta-table">
                    <tr>
                        <td class="cta-cell" align="center"><a href="${link}" class="email-cta">${msg("passwordReset.cta")} &nbsp;→</a></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <div class="info-box">
        <div class="info-card">
            <p><strong>${msg("email.expiryLabel")}</strong> &nbsp; ${msg("email.expiryValue", linkExpiration)}</p>
        </div>
    </div>
    <p style="margin-top:18px;">${msg("email.suspiciousFooter")}</p>
</@layout.emailLayout>
