<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow=msg("eventLoginError.eyebrow")
    title=msg("eventLoginError.title")
    subtitle=msg("eventLoginError.subtitle")>
    <p>${msg("email.greeting")} <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>${msg("eventLoginError.body1")}</p>
    <div class="info-box">
        <div class="info-card">
            <p><strong>${msg("eventLoginError.ipLabel")}</strong> &nbsp; <span class="num">${event.ipAddress!'-'}</span></p>
        </div>
    </div>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="cta-wrap" align="left">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" class="cta-table">
                    <tr>
                        <td class="cta-cell" align="center"><a href="${link}" class="email-cta">${msg("eventLoginError.cta")} &nbsp;→</a></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <p style="margin-top:18px;">${msg("eventLoginError.footer")}</p>
</@layout.emailLayout>
