<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow="DOĞRULAMA KODU"
    title="E-posta adresini doğrula"
    subtitle="Aşağıdaki kodu açık olan doğrulama sayfasına gir.">
    <p>Merhaba <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>Hesabına bağlı yeni e-posta adresini onaylamak için tek seferlik 6 haneli kodu kullan.</p>
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
            <p><strong>Geçerlilik</strong> &nbsp; Kod <span class="num">${linkExpiration}</span> dakika sonra geçersizleşir. Yeni kod istemen gerekirse e-posta değiştirme akışını yeniden başlat.</p>
        </div>
    </div>
    <p style="margin-top:18px;">İşlemi sen başlatmadıysan bu e-postayı yok sayabilirsin — kod geçersiz olduğu sürece hesabın etkilenmez.</p>
</@layout.emailLayout>
