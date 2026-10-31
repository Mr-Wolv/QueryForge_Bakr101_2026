<#import "email.ftl" as layout>
<#layout.layout(layout, "Verify your QueryForge account") {
<p>Hello ${user.firstName!""}${user.lastName?if_exists ", "}</p>

<p>Welcome to QueryForge!</p>

<p>Please verify your email address by clicking the link below:</p>

<table>
  <tr>
    <td class="button-cell">
      <a class="button" href="${verifyUrl}">Verify your email</a>
    </td>
  </tr>
</table>

<p>— The QueryForge team</p>
} </#layout.layout>
