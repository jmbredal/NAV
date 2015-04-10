#
# Copyright (C) 2014 UNINETT AS
#
# This file is part of Network Administration Visualized (NAV).
#
# NAV is free software: you can redistribute it and/or modify it under
# the terms of the GNU General Public License version 2 as published by
# the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
# more details.  You should have received a copy of the GNU General Public
# License along with NAV. If not, see <http://www.gnu.org/licenses/>.
#
"""Controllers for WatchDog requests"""
from django.shortcuts import render
from django.http import JsonResponse

from nav.models.fields import INFINITY
from nav.models.manage import Arp, Device
from nav.web.utils import create_title
from nav.watchdog.util import get_statuses


def render_index(request):
    """Controller for WatchDog index"""
    navpath = [('Home', '/'), ('WatchDog', )]

    context = {
        'navpath': navpath,
        'title': create_title(navpath),
        'tests': get_statuses()
    }

    return render(request, 'watchdog/base.html', context)


def get_active_addresses(_):
    """Get active addresses on the network"""
    active = Arp.objects.filter(end_time=INFINITY)
    num_active = active.count()
    num_active_ipv6 = active.extra(where=['family(ip)=6']).count()
    num_active_ipv4 = active.extra(where=['family(ip)=4']).count()
    return JsonResponse({
        'active': num_active,
        'ipv6': num_active_ipv6,
        'ipv4': num_active_ipv4
    })


def get_serial_numbers(_):
    """Get number of distinct serial numbers in NAV"""
    return JsonResponse(Device.objects.distinct('serial').count())
