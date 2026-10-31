<#import "email.ftl" as layout>
<#layout.layout(layout, "Reset your QueryForge password") {
<p>Hello ${user.firstName!""}${user.lastName?if_exists ", "}</p>

<p>You requested a password reset for your QueryForge account.</p>

<p>Use the link below to set a new password:</p>

<table>
  <tr>
    <td class="button-cell">
      <a class="button" href="${resetUrl}">Reset your password</a>
    </td>
  </tr>
</table>

<p>This link will expire shortly. If you did not request this reset, you can ignore this email.</p>

<p>— The QueryForge team</p>
} </#layout.layout>
