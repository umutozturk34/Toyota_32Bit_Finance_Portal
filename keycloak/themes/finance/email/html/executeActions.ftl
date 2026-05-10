<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow=msg("executeActions.eyebrow")
    title=msg("executeActions.title")
    subtitle=msg("executeActions.subtitle")>
    <p>${msg("email.greeting")} <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>${msg("executeActions.body1")}</p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="cta-wrap" align="left">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" class="cta-table">
                    <tr>
                        <td class="cta-cell" align="center"><a href="${link}" class="email-cta">${msg("executeActions.cta")} &nbsp;→</a></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <div class="info-box">
        <div class="info-card">
            <p><strong>${msg("email.expiryLabel")}</strong> &nbsp; ${msg("executeActions.expiryHint", linkExpiration)}</p>
        </div>
    </div>
    <p style="margin-top:18px;">${msg("executeActions.footer")}</p>
</@layout.emailLayout>
