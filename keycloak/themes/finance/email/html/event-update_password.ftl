<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow="GÜVENLİK BİLDİRİMİ"
    title="Hesap şifren güncellendi"
    subtitle="Bu değişikliği sen yaptıysan ek bir aksiyon gerekmez.">
    <p>Merhaba <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>Finance Portal hesabında bir az önce <strong>şifre değişikliği</strong> tespit edildi. Eski şifren artık geçersiz.</p>
    <div class="info-box">
        <div class="info-card">
            <p><strong>Bu işlemi sen yapmadıysan</strong> &nbsp; Hesabın risk altında olabilir. Aşağıdaki bağlantıdan şifreni sıfırla ve oturumlarını gözden geçir.</p>
        </div>
    </div>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="cta-wrap" align="left">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" class="cta-table">
                    <tr>
                        <td class="cta-cell" align="center"><a href="${link}" class="email-cta">Şifreyi Sıfırla &nbsp;→</a></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <p style="margin-top:18px;">Şüpheli bir oturum görüyorsan iki adımlı doğrulamayı (2FA) aktifleştirmeni öneririz.</p>
</@layout.emailLayout>
