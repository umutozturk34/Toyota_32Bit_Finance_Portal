<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow="HESAP DOĞRULAMA"
    title="E-posta adresini doğrula"
    subtitle="${user.email!''}">
    <p>Merhaba <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>Aşağıdaki tek kullanımlık kodu açık olan doğrulama sayfasına gir.</p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="code-block">
                <div class="code-frame">
                    <p class="code-eyebrow">tek kullanımlık kod</p>
                    <span class="email-code">${code}</span>
                </div>
            </td>
        </tr>
    </table>
    <div class="info-box">
        <div class="info-card">
            <p><strong>Geçerlilik</strong> &nbsp; Kod <span class="num">${linkExpiration}</span> dakika sonra sona erer.</p>
        </div>
    </div>
    <p style="margin-top:18px;">Bu işlemi sen başlatmadıysan e-postayı yok sayabilirsin.</p>
</@layout.emailLayout>
