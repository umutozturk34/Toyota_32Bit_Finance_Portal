<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow="ŞÜPHELİ GİRİŞ"
    title="Hesabında yeni bir oturum açıldı"
    subtitle="Bilinmeyen bir cihaz ya da konumdan gelen erişim tespit edildi.">
    <p>Merhaba <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>Hesabına az önce yeni bir oturum açıldı. İşlem sen değilsen aşağıdaki adımları uygulayarak hesabını güvene al.</p>
    <div class="info-box">
        <div class="info-card">
            <p><strong>IP adresi</strong> &nbsp; <span class="num">${event.ipAddress!'-'}</span></p>
        </div>
    </div>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="cta-wrap" align="left">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" class="cta-table">
                    <tr>
                        <td class="cta-cell" align="center"><a href="${link}" class="email-cta">Şifreyi Değiştir &nbsp;→</a></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <p style="margin-top:18px;">Tüm aktif oturumları kapatıp 2FA'yı kurarak hesabını ek bir katman ile koruyabilirsin.</p>
</@layout.emailLayout>
