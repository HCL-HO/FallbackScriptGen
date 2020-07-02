package com.eh.kotlin.email

import com.eh.kotlin.CommandHelper
import com.eh.kotlin.db.ConnectionManager
import java.lang.StringBuilder
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@Suppress("UNCHECKED_CAST")
class EmailUpdater {

    companion object {
        private const val REG_SET_PARM = "(\\S*?)@(.*?)($|(?=;))"
        private const val MSG_CHOOSE_ADD = "Choose ADD Emails: "
        private const val MSG_CHOOSE_DELETE = "Choose DEL Emails: "
        private const val MSG_CHOOSE_Types = "Choose Types: "
        private const val MSG_EFF = "EFFECTIVE DATE (dd/mm/yyyy): "
        private const val MSG_MERC = "Merchant: "

        private const val TAG_MERCHANT = "MERCHANT"
        private const val TAG_EFF_DATE = "EFFDATE"
        private const val TAG_FALLBACK: String = "TAG_FALLBACK"
        private const val TAG_IMP_PLAM: String = "TAG_IMP"
        private const val TAG_ORDER = "TAG_ORDER"
        private const val TAG_MAILS: String = "TAGMAILS"

        var order = 1

        private val types = arrayListOf<String>(
            "Notification of Payment File of Hongkong Post PayThruPost Service",
            "Daily Report of Hongkong Post PayThruPost Service",
            "Reminder of Changing SFTP Account Password of Hongkong Post PayThruPost Service"
        )

        @JvmStatic
        fun main(args: Array<String>) {
            val noOfReqest = CommandHelper.waitNumber("Number of Request: ", 1)
            val requests: MutableList<Request> = ArrayList<Request>()

            val emailsContent = CommandHelper.waitInput("Please paste the email content: ")

            for (x in 1..noOfReqest) {
                val mails = getEmails(emailsContent)
                println(MSG_CHOOSE_Types)
                val type = CommandHelper.chooseOne(types)
                println(MSG_CHOOSE_ADD)
                val addMails = CommandHelper.chooseMany(mails)
                println(MSG_CHOOSE_DELETE)
                val delMails = CommandHelper.chooseMany(mails)
                val effDate = CommandHelper.waitInput(MSG_EFF)
                val merchant = CommandHelper.waitInput(MSG_MERC)
                requests.add(Request(type, addMails, delMails, effDate, merchant))
            }
            var imp_analysis: String = HCMS_IMPACT_ANALYSIS
            requests.forEachIndexed { index, request ->
                imp_analysis = getImplactAnalysis(imp_analysis, index == requests.size - 1, request)
            }

            println(imp_analysis)

        }

        private fun getImplactAnalysis(imp_analysis: String, isLast: Boolean, request: Request): String {
            val PLAN = when (request.type) {
                types[0] -> TEMP_PAYMENT_FILE
                types[1] -> TEMP_REPOT
                types[2] -> TEMP_CHG_PWD
                else -> TEMP_PAYMENT_FILE
            }

            val SQL = when (request.type) {
                types[0] -> SQL_PAY_FILE.replace("AXA", request.merchant)
                types[1] -> SQL_RPT.replace("AXA", request.merchant)
                types[2] -> SQL_CHG_PWD.replace("AXA", request.merchant)
                else -> SQL_PAY_FILE.replace("AXA", request.merchant)
            }

            val mailsExistingAny = ConnectionManager.query(SQL) {
                if (it.next()) {
                    return@query it.getString(1)
                } else {
                    println("No mails for ${request.type}")
                    return@query ""
                }
            }

            val oldMailList: MutableList<String> = ArrayList<String>()
            oldMailList.addAll((mailsExistingAny as String).split(","))
            var FB_PLAN =
                PLAN.replace(TAG_MAILS, oldMailList.toDBString()).replace(TAG_MERCHANT, request.merchant).replace(
                    TAG_ORDER, order.toString()
                )
            request.addMails.forEach { oldMailList.add(it) }
            request.delMails.forEach { oldMailList.remove(it) }
            var IMP_PLAN =
                PLAN.replace(TAG_MAILS, oldMailList.toDBString()).replace(TAG_MERCHANT, request.merchant).replace(
                    TAG_ORDER, order.toString()
                )
            if (!isLast) {
                IMP_PLAN += "\n$TAG_IMP_PLAM"
                FB_PLAN += "\n$TAG_FALLBACK"
            }
            val result = imp_analysis.replace(TAG_MERCHANT, request.merchant)
                .replace(TAG_EFF_DATE, request.effDate)
                .replace(TAG_IMP_PLAM, IMP_PLAN)
                .replace(TAG_FALLBACK, FB_PLAN)
            order += 1
            return result
        }

        private fun getEmails(longStr: String): HashSet<String> {
            val mails = HashSet<String>()
            val pattern = Pattern.compile(REG_SET_PARM, Pattern.MULTILINE)
            val matcher = pattern.matcher(longStr)
            while (matcher.find()) {
                mails.add(matcher.group(0).trimStart().trimEnd())
            }
            return mails
        }

        val HCMS_IMPACT_ANALYSIS = "*Please approve the following change.\n" +
                "\n" +
                "1) Description of Change: \n" +
                "Update recipient list for ${TAG_MERCHANT}\n" +
                "\n" +
                "2) Impact of Change: \n" +
                "Nil\n" +
                "\n" +
                "3) Deployment Meeting Date: \n" +
                "N/A\n" +
                "\n" +
                "4) UAT Confirmed by : \n" +
                "N/A\n" +
                "\n" +
                "5) Implementation Date: DD/MM/YY\n" +
                "${TAG_EFF_DATE}\n" +
                "\n" +
                "6) Implementation Plan:\n" +
                "${TAG_IMP_PLAM}\n" +
                "7) Contingency / Fall-back Plan: \n" + TAG_FALLBACK

        private const val TEMP_PAYMENT_FILE =
            "${TAG_ORDER}) In Email Address Maintenance function, choose “Notification of payment file” \t   \n" +
                    "- Search \"${TAG_MERCHANT}\"\n" +
                    "- Update recipient email address:\n" +
                    "    To: ${TAG_MAILS}\n" +
                    "\n"

        private const val TEMP_REPOT =
            "${TAG_ORDER}) In Email Address Maintenance function, choose “Notification of report” \n" +
                    "- Search \"${TAG_MERCHANT}\"\n" +
                    "- Update recipient email address:\n" +
                    "    To: ${TAG_MAILS}\n" +
                    "\t\n"

        private const val TEMP_CHG_PWD =
            "${TAG_ORDER}) In Email Address Maintenance function, choose “Notification of change password” \n" +
                    "- Search \"${TAG_MERCHANT}\"\n" +
                    "- Update recipient email address:\n" +
                    "    To: ${TAG_MAILS}\n"

        private const val SQL_CHG_PWD = "Select a.CHG_PWD_RECP_EMAIL From C_Pay_Sftp_Acc_Info a,  c_pay_merh b \n" +
                "Where (Trunc(Sysdate) Between a.Eff_Date And a.Exp_Date) \n" +
                "and a.merh_code = b.merh_code and b.abbr = 'AXA'"

        private const val SQL_RPT = "select a.RECP_EMAIL from C_Pay_Rpt_Email_Info a, c_pay_merh b \n" +
                "where a.merh_code = b.merh_code and b.abbr = 'AXA' \n" +
                "and (sysdate between a.eff_date and a.exp_date)"

        private const val SQL_PAY_FILE = "select a.RECIPIENT from c_pay_email a, c_pay_merh b \n" +
                "Where (Trunc(Sysdate) Between a.Eff_Date And a.Exp_Date) \n" +
                "and a.merh_code = b.merh_code and b.abbr = 'AXA'"

    }


    data class Request(
        var type: String,
        var addMails: Collection<String>,
        var delMails: Collection<String>,
        var effDate: String,
        var merchant: String
    )
}

private fun <String> Collection<String>.toDBString(): kotlin.String {
    val sb = StringBuilder()
    this.forEachIndexed { index, string ->
        sb.append(string)
        if (index != this.size - 1) {
            sb.append(",")
        }
    }
    return sb.toString()
}
