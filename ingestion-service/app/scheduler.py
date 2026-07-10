from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
import asyncio

def start_scheduler(cron_expr: str, run_sweep_now):
    sched = BackgroundScheduler()
    def job():
        asyncio.run(run_sweep_now("SCHEDULED"))
    sched.add_job(job, CronTrigger.from_crontab(cron_expr), id="sweep", replace_existing=True)
    sched.start()
    return sched
