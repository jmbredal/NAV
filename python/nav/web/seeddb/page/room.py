# -*- coding: utf-8 -*-
#
# Copyright (C) 2011 UNINETT AS
#
# This file is part of Network Administration Visualized (NAV).
#
# NAV is free software: you can redistribute it and/or modify it under the
# terms of the GNU General Public License version 2 as published by the Free
# Software Foundation.
#
# This program is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
# details.  You should have received a copy of the GNU General Public License
# along with NAV. If not, see <http://www.gnu.org/licenses/>.
#
"""Forms and view functions for SeedDB's Room view"""
from django import forms

from nav.models.manage import Location, Room
from nav.bulkparse import RoomBulkParser
from nav.bulkimport import RoomImporter

from nav.web.seeddb import SeeddbInfo, reverse_lazy
from nav.web.seeddb.constants import SEEDDB_EDITABLE_MODELS
from nav.web.seeddb.page import view_switcher
from nav.web.seeddb.utils.list import render_list
from nav.web.seeddb.utils.edit import render_edit
from nav.web.seeddb.utils.delete import render_delete
from nav.web.seeddb.utils.move import move
from nav.web.seeddb.utils.bulk import render_bulkimport


class RoomFilterForm(forms.Form):
    """Form for filtering rooms"""
    location = forms.ModelChoiceField(
        Location.objects.order_by('id').all(), required=False)


class RoomForm(forms.ModelForm):
    """Form for editing/adding rooms"""
    location = forms.ModelChoiceField(queryset=Location.objects.order_by('id'))

    class Meta:
        model = Room


class RoomMoveForm(forms.Form):
    """Form for moving a room to a new location"""
    location = forms.ModelChoiceField(
        Location.objects.order_by('id').all(), required=False)


class RoomInfo(SeeddbInfo):
    """Room info object"""
    active = {'room': True}
    caption = 'Rooms'
    tab_template = 'seeddb/tabs_room.html'
    _title = 'Rooms'
    _navpath = [('Rooms', reverse_lazy('seeddb-room'))]
    delete_url = reverse_lazy('seeddb-room')


def room(request):
    """Controller for listing, moving and deleting rooms"""
    return view_switcher(request,
                         list_view=room_list,
                         move_view=room_move,
                         delete_view=room_delete)


def room_list(request):
    """Controller for listing rooms. Used in room()"""
    info = RoomInfo()
    value_list = (
        'id', 'location', 'description', 'position', 'optional_1',
        'optional_2', 'optional_3', 'optional_4')
    query = Room.objects.all()
    filter_form = RoomFilterForm(request.GET)
    return render_list(request, query, value_list, 'seeddb-room-edit',
                       filter_form=filter_form,
                       extra_context=info.template_context)


def room_move(request):
    """Controller for moving rooms. Used in room()"""
    info = RoomInfo()
    return move(request, Room, RoomMoveForm, 'seeddb-room',
                extra_context=info.template_context)


def room_delete(request):
    """Controller for deleting rooms. Used in room()"""
    info = RoomInfo()
    return render_delete(request, Room, 'seeddb-room',
                         whitelist=SEEDDB_EDITABLE_MODELS,
                         extra_context=info.template_context)


def room_edit(request, room_id=None):
    """Controller for editing a room"""
    info = RoomInfo()
    extra_context = {'map': True}
    extra_context.update(info.template_context)
    return render_edit(request, Room, RoomForm, room_id,
                       'seeddb-room-edit',
                       extra_context=extra_context)


def room_bulk(request):
    """Controller for bulk editing rooms"""
    info = RoomInfo()
    return render_bulkimport(
        request, RoomBulkParser, RoomImporter,
        'seeddb-room',
        extra_context=info.template_context)
