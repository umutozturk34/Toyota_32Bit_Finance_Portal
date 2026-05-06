<#import "template.ftl" as layout>
<@layout.emailLayout
    eyebrow="HESAP İŞLEMİ"
    title="Tamamlanması gereken bir adım var"
    subtitle="Hesabın güvenliği için aşağıdaki işlemi onayla.">
    <p>Merhaba <strong>${user.firstName!user.username!''}</strong>,</p>
    <p>Hesabın için tamamlanmayı bekleyen bir aksiyon var. Aşağıdaki butonla güvenli sayfaya geçerek işlemi onaylayabilirsin.</p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr>
            <td class="cta-wrap" align="left">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" class="cta-table">
                    <tr>
                        <td class="cta-cell" align="center"><a href="${link}" class="email-cta">İşlemi Tamamla &nbsp;→</a></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <div class="info-box">
        <div class="info-card">
            <p><strong>Geçerlilik</strong> &nbsp; Bağlantı <span class="num">${linkExpiration}</span> dakika sonra sona erer. Süre dolarsa işlemi yeniden başlatman gerekir.</p>
        </div>
    </div>
    <p style="margin-top:18px;">Bu talebi sen başlatmadıysan e-postayı yok say. Bağlantıyı açmadığın sürece hesabında değişiklik yapılmaz.</p>
</@layout.emailLayout>
