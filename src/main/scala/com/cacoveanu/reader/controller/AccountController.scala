package com.cacoveanu.reader.controller

import com.cacoveanu.reader.service.UserService
import com.cacoveanu.reader.util.SessionUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{RequestMapping, RequestMethod, ResponseBody}
import org.springframework.web.servlet.view.RedirectView

import scala.beans.BeanProperty

@Controller
class AccountController @Autowired()(private val accountService: UserService) {

  @RequestMapping(value=Array("/password"), method = Array(RequestMethod.GET))
  def passwordResetPage(): String = "passwordReset"

  @RequestMapping(
    value=Array("/password"),
    method=Array(RequestMethod.POST),
    consumes=Array(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  )
  @ResponseBody
  def passwordResetAction(body: ChangePasswordForm): RedirectView = {
    if (body.newPassword != body.newPasswordConfirm) {
      new RedirectView("/password?error")
    } else {
      if (accountService.changePassword(SessionUtil.getUser(), body.oldPassword, body.newPassword))
        new RedirectView("/")
      else
        new RedirectView("/password?error")
    }
  }
}

class ChangePasswordForm {
  @BeanProperty var oldPassword: String = _
  @BeanProperty var newPassword: String = _
  @BeanProperty var newPasswordConfirm: String = _
}