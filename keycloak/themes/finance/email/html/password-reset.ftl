<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow="ŞİFRE SIFIRLAMA"
    title="Yeni şifreni belirle"
    subtitle="Aşağıdaki bağlantı yalnızca senin için oluşturuldu.">
    <p>Merhaba <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>Şifre sıfırlama talebinde bulundun. Aşağıdaki butona tıklayarak güvenli sayfada yeni şifreni belirleyebilirsin.</p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="cta-wrap" align="left">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" class="cta-table">
                    <tr>
                        <td class="cta-cell" align="center"><a href="${link}" class="email-cta">Şifremi Sıfırla &nbsp;→</a></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <div class="info-box">
        <div class="info-card">
            <p><strong>Geçerlilik</strong> &nbsp; Bağlantı <span class="num">${linkExpiration}</span> dakika sonra geçersizleşir.</p>
        </div>
    </div>
    <p style="margin-top:18px;">Bu işlemi sen başlatmadıysan hesabın güvende — bağlantıyı kullanmadığın sürece şifren değişmez. Şüpheli bir aktivite varsa destek ile iletişime geç.</p>
</@layout.emailLayout>
