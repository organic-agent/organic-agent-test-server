package com.soma.testwes.support

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    /** 아이디·비밀번호가 틀린 경우. 어느 쪽이 틀렸는지는 알려주지 않는다. */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(exception: AuthenticationException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다")

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(exception: ForbiddenException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, exception.message)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(exception: NotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, exception.message)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, exception.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ProblemDetail {
        val detail = exception.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return problem(HttpStatus.BAD_REQUEST, detail)
    }

    private fun problem(status: HttpStatus, detail: String?): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail ?: status.reasonPhrase)
}
