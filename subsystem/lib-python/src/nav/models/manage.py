# -*- coding: utf-8 -*-
#
# Copyright 2007 UNINETT AS
#
# This file is part of Network Administration Visualized (NAV)
#
# NAV is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# NAV is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with NAV; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
#
# Authors: Stein Magnus Jodal <stein.magnus.jodal@uninett.no>
#

"""Django ORM wrapper for the NAV manage database"""

__copyright__ = "Copyright 2007 UNINETT AS"
__license__ = "GPL"
__author__ = "Stein Magnus Jodal (stein.magnus.jodal@uninett.no)"
__id__ = "$Id$"

from datetime import datetime

from django.db import models

# Choices used in multiple models, "imported" into the models which use them
LINK_UP = 'y'
LINK_DOWN = 'n'
LINK_DOWN_ADM = 'd'
LINK_CHOICES = (
    (LINK_UP, 'up'), # In old devBrowser: 'Active'
    (LINK_DOWN, 'down (operDown)'), # In old devBrowser: 'Not active'
    (LINK_DOWN_ADM, 'down (admDown)'), # In old devBrowser: 'Denied'
)

#######################################################################
### Netbox-related models

class Netbox(models.Model):
    UP_UP = 'y'
    UP_DOWN = 'n'
    UP_SHADOW = 's'
    UP_CHOICES = (
        (UP_UP, 'up'),
        (UP_DOWN, 'down'),
        (UP_SHADOW, 'shadow'),
    )
    id = models.IntegerField(db_column='netboxid', primary_key=True)
    ip = models.IPAddressField(unique=True)
    room = models.ForeignKey('Room', db_column='roomid')
    type = models.ForeignKey('NetboxType', db_column='typeid')
    device = models.ForeignKey('Device', db_column='deviceid')
    sysname = models.CharField(unique=True, max_length=-1)
    category = models.ForeignKey('Category', db_column='catid')
    subcategory = models.CharField(db_column='subcat', max_length=-1)
    organization = models.ForeignKey('Organization', db_column='orgid')
    read_only = models.CharField(db_column='ro', max_length=-1)
    read_write = models.CharField(db_column='rw', max_length=-1)
    prefix = models.ForeignKey('Prefix', db_column='prefixid')
    up = models.CharField(max_length=1, choices=UP_CHOICES, default=UP_UP)
    snmp_version = models.IntegerField()
    snmp_agent = models.CharField(max_length=-1)
    up_since = models.DateTimeField(db_column='upsince')
    up_to_date = models.BooleanField(db_column='uptodate')
    discovered = models.DateTimeField()
    class Meta:
        db_table = 'netbox'

class NetboxInfo(models.Model):
    id = models.IntegerField(db_column='netboxinfoid', primary_key=True)
    netbox = models.ForeignKey('Netbox', db_column='netboxid')
    key = models.CharField(max_length=-1)
    variable = models.CharField(db_column='var', max_length=-1)
    value = models.TextField(db_column='val')
    class Meta:
        db_table = 'netboxinfo'
        unique_together = (('netbox', 'key', 'variable', 'value'),)

class Device(models.Model):
    id = models.IntegerField(db_column='deviceid', primary_key=True)
    product = models.ForeignKey('Product', db_column='productid')
    serial = models.CharField(unique=True, max_length=-1)
    hardware_version = models.CharField(db_column='hw_ver', max_length=-1)
    firmware_version = models.CharField(db_column='fw_ver', max_length=-1)
    software_version = models.CharField(db_column='sw_ver', max_length=-1)
    auto = models.BooleanField(default=False)
    active = models.BooleanField(default=False)
    device_order = models.ForeignKey('DeviceOrder', db_column='deviceorderid')
    discovered = models.DateTimeField(default=datetime.now)
    class Meta:
        db_table = 'device'

class Module(models.Model):
    UP_UP = 'y'
    UP_DOWN = 'n'
    UP_CHOICES = (
        (UP_UP, 'up'),
        (UP_DOWN, 'down'),
    )
    id = models.IntegerField(db_column='moduleid', primary_key=True)
    device = models.ForeignKey('Device', db_column='deviceid')
    netbox = models.ForeignKey('Netbox', db_column='netboxid')
    module_number = models.IntegerField(db_column='module')
    model = models.CharField(max_length=-1)
    description = models.CharField(db_column='descr', max_length=-1)
    up = models.CharField(max_length=1, choices=UP_CHOICES, default=UP_UP)
    down_since = models.DateTimeField(db_column='downsince')
    community_suffix = models.CharField(max_length=-1)
    class Meta:
        db_table = 'module'
        unique_together = (('netbox', 'module_number'),)

class Memory(models.Model):
    id = models.IntegerField(db_column='memid', primary_key=True)
    netbox = models.ForeignKey('Netbox', db_column='netboxid')
    type = models.CharField(db_column='memtype', max_length=-1)
    device = models.CharField(max_length=-1)
    size = models.IntegerField()
    used = models.IntegerField()
    class Meta:
        db_table = 'mem'
        unique_together = (('netbox', 'type', 'device'),)

class Room(models.Model):
    id = models.CharField(db_column='roomid', max_length=30, primary_key=True)
    location = models.ForeignKey('Location', db_column='locationid')
    description = models.CharField(db_column='descr', max_length=-1)
    optional_1 = models.CharField(db_column='opt1', max_length=-1)
    optional_2 = models.CharField(db_column='opt2', max_length=-1)
    optional_3 = models.CharField(db_column='opt3', max_length=-1)
    optional_4 = models.CharField(db_column='opt4', max_length=-1)
    class Meta:
        db_table = 'room'

class Location(models.Model):
    id = models.CharField(db_column='locationid',
        max_length=30, primary_key=True)
    descr = models.CharField(max_length=-1)
    class Meta:
        db_table = 'location'

class Organization(models.Model):
    id = models.CharField(db_column='orgid', max_length=30, primary_key=True)
    parent = models.ForeignKey('self', db_column='parent')
    description = models.CharField(db_column='descr', max_length=-1)
    optional_1 = models.CharField(db_column='opt1', max_length=-1)
    optional_2 = models.CharField(db_column='opt2', max_length=-1)
    optional_3 = models.CharField(db_column='opt3', max_length=-1)
    class Meta:
        db_table = 'org'

class Category(models.Model):
    id = models.CharField(db_column='catid', max_length=8, primary_key=True)
    description = models.CharField(db_column='descr', max_length=-1)
    req_snmp = models.BooleanField()
    class Meta:
        db_table = 'cat'

class Subcategory(models.Model):
    id = models.CharField(db_column='subcatid', max_length=-1, primary_key=True)
    description = models.CharField(db_column='descr', max_length=-1)
    category = models.ForeignKey('Category', db_column='catid')
    class Meta:
        db_table = 'subcat'

class NetboxCategory(models.Model):
    # TODO: This should be a ManyToMany-field in Netbox, but at this time
    # Django only supports specifying the name of the M2M-table, and not the
    # column names.
    netbox = models.ForeignKey('Netbox', db_column='netboxid')
    category = models.ForeignKey('Subcategory', db_column='category')
    class Meta:
        db_table = 'netboxcategory'
        unique_together = (('netbox', 'category'),) # The primary key

class NetboxType(models.Model):
    id = models.IntegerField(db_column='typeid', primary_key=True)
    vendor = models.ForeignKey('Vendor', db_column='vendorid')
    name = models.CharField(db_column='typename', max_length=-1)
    sysobject = models.CharField(db_column='sysobjectid',
        unique=True, max_length=-1)
    cdp = models.BooleanField(default=False)
    tftp = models.BooleanField(default=False)
    cs_at_vlan = models.BooleanField()
    chassis = models.BooleanField(default=True)
    frequency = models.IntegerField()
    descr = models.CharField(max_length=-1)
    class Meta:
        db_table = 'type'
        unique_together = (('vendor', 'name'),)

#######################################################################
### Device management

class Vendor(models.Model):
    id = models.CharField(db_column='vendorid', max_length=15, primary_key=True)
    enterprise_id = models.IntegerField(db_column='enterpriseid')
    class Meta:
        db_table = 'vendor'

class Product(models.Model):
    id = models.IntegerField(db_column='productid', primary_key=True)
    vendor = models.ForeignKey('Vendor', db_column='vendorid')
    product_number = models.CharField(db_column='productno', max_length=-1)
    description = models.CharField(db_column='descr', max_length=-1)
    class Meta:
        db_table = 'product'
        unique_together = (('vendor', 'product_number'),)

class DeviceOrder(models.Model):
    id = models.IntegerField(db_column='deviceorderid', primary_key=True)
    registered = models.DateTimeField(default=datetime.now)
    ordered = models.DateField()
    arrived = models.DateTimeField()
    order_number = models.CharField(db_column='ordernumber', max_length=-1)
    comment = models.CharField(max_length=-1)
    retailer = models.CharField(max_length=-1)
    username = models.CharField(max_length=-1)
    organization = models.ForeignKey('Organization', db_column='orgid')
    product = models.ForeignKey('Product', db_column='productid')
    updated_by = models.CharField(db_column='updatedby', max_length=-1)
    last_updated = models.DateField(db_column='lastupdated')
    class Meta:
        db_table = 'deviceorder'

#######################################################################
### Router/topology

class GwPort(models.Model):
    LINK_UP = LINK_UP
    LINK_DOWN = LINK_DOWN
    LINK_DOWN_ADM = LINK_DOWN_ADM
    LINK_CHOICES = LINK_CHOICES
    id = models.IntegerField(db_column='gwportid', primary_key=True)
    module = models.ForeignKey('Module', db_column='moduleid')
    ifindex = models.IntegerField()
    link = models.CharField(max_length=1, choices=LINK_CHOICES)
    master_index = models.IntegerField(db_column='masterindex')
    interface = models.CharField(max_length=-1)
    speed = models.FloatField()
    metric = models.IntegerField()
    to_netbox = models.ForeignKey('Netbox', db_column='to_netboxid')
    to_swport = models.ForeignKey('SwPort', db_column='to_swportid')
    port_name = models.CharField(db_column='portname', max_length=-1)
    class Meta:
        db_table = 'gwport'
        unique_together = (('module', 'ifindex'),)

class GwPortPrefix(models.Model):
    gwport = models.ForeignKey('GwPort', db_column='gwportid')
    prefix = models.ForeignKey('Prefix', db_column='prefixid')
    gw_ip = models.IPAddressField(db_column='gwip', unique=True)
    hsrp = models.BooleanField(default=False)
    class Meta:
        db_table = 'gwportprefix'

class Prefix(models.Model):
    id = models.IntegerField(db_column='prefixid', primary_key=True)
    # TODO: Create CIDRField
    net_address = models.TextField(db_column='netaddr', unique=True)
    vlan = models.ForeignKey('Vlan', db_column='vlanid')
    class Meta:
        db_table = 'prefix'

class Vlan(models.Model):
    id = models.IntegerField(db_column='vlanid', primary_key=True)
    vlan = models.IntegerField()
    net_type = models.ForeignKey('NetType', db_column='nettype')
    organization = models.ForeignKey('Organization', db_column='orgid')
    usage = models.ForeignKey('Usage', db_column='usageid')
    net_ident = models.CharField(db_column='netident', max_length=-1)
    description = models.CharField(max_length=-1)
    class Meta:
        db_table = 'vlan'

class NetType(models.Model):
    id = models.CharField(db_column='nettypeid',
        max_length=-1, primary_key=True)
    description = models.CharField(db_column='descr', max_length=-1)
    edit = models.BooleanField(default=False)
    class Meta:
        db_table = 'nettype'

class Usage(models.Model):
    id = models.CharField(db_column='usageid',
        max_length=30, primary_key=True)
    description = models.CharField(db_column='descr', max_length=-1)
    class Meta:
        db_table = 'usage'

class Arp(models.Model):
    id = models.IntegerField(db_column='arpid', primary_key=True)
    netbox = models.ForeignKey('Netbox', db_column='netboxid')
    prefix = models.ForeignKey('Prefix', db_column='prefixid')
    sysname = models.CharField(max_length=-1)
    ip = models.IPAddressField()
    # TODO: Create MACAddressField
    mac = models.TextField()
    start_time = models.DateTimeField()
    end_time = models.DateTimeField()
    class Meta:
        db_table = 'arp'

#######################################################################
### Switch/topology

class SwPort(models.Model):
    LINK_UP = LINK_UP
    LINK_DOWN = LINK_DOWN
    LINK_DOWN_ADM = LINK_DOWN_ADM
    LINK_CHOICES = LINK_CHOICES
    DUPLEX_FULL = 'f'
    DUPLEX_HALF = 'h'
    DUPLEX_CHOICES = (
        (DUPLEX_FULL, 'full duplex'),
        (DUPLEX_HALF, 'half duplex'),
    )
    id = models.IntegerField(db_column='swportid', primary_key=True)
    module = models.ForeignKey('Module', db_column='moduleid')
    ifindex = models.IntegerField()
    port = models.IntegerField()
    interface = models.CharField(max_length=-1)
    link = models.CharField(max_length=1, choices=LINK_CHOICES)
    speed = models.FloatField()
    duplex = models.CharField(max_length=1, choices=DUPLEX_CHOICES)
    media = models.CharField(max_length=-1)
    vlan = models.IntegerField()
    trunk = models.BooleanField()
    portname = models.CharField(max_length=-1)
    to_netbox = models.ForeignKey('Netbox', db_column='to_netboxid')
    to_swport = models.ForeignKey('self', db_column='to_swportid')
    class Meta:
        db_table = 'swport'
        unique_together = (('module', 'ifindex'),)

class SwPortVlan(models.Model):
    DIRECTION_UNDEFINED = 'u'
    DIRECTION_UP = 'o'
    DIRECTION_DOWN = 'd'
    DIRECTION_BOTH = 'b'
    DIRECTION_CROSSED = 'x'
    DIRECTION_CHOICES = (
        (DIRECTION_UNDEFINED, 'undefined'),
        (DIRECTION_UP, 'up'),
        (DIRECTION_DOWN, 'down'),
        (DIRECTION_BOTH, 'both'),
        (DIRECTION_CROSSED, 'crossed'),
    )
    id = models.IntegerField(db_column='swportvlanid', primary_key=True)
    swport = models.ForeignKey('SwPort', db_column='swportid')
    vlan = models.ForeignKey('Vlan', db_column='vlanid')
    direction = models.CharField(max_length=1, choices=DIRECTION_CHOICES,
        default=DIRECTION_CROSSED)
    class Meta:
        db_table = 'swportvlan'
        unique_together = (('swport', 'vlan'),)

class SwPortAllowedVlan(models.Model):
    swport = models.ForeignKey('SwPort', db_column='swportid')
    hex_string = models.CharField(db_column='hexstring', max_length=-1)
    class Meta:
        db_table = 'swportallowedvlan'

class SwPortBlocked(models.Model):
    swport = models.ForeignKey('SwPort', db_column='swportid')
    vlan = models.IntegerField()
    class Meta:
        db_table = 'swportblocked'
        unique_together = (('swport', 'vlan'),) # Primary key

class SwPortToNetbox(models.Model):
    id = models.IntegerField(db_column='swp_netboxid', primary_key=True)
    netbox = models.ForeignKey('Netbox', db_column='netboxid')
    ifindex = models.IntegerField()
    to_netbox = models.ForeignKey('Netbox', db_column='to_netboxid',
        related_name='candidate_for_next_hop_set')
    to_swport = models.ForeignKey('SwPort', db_column='to_swportid',
        related_name='candidate_for_next_hop_set')
    miss_count = models.IntegerField(db_column='misscnt', default=0)
    class Meta:
        db_table = 'swp_netbox'
        unique_together = (('netbox', 'ifindex', 'to_netbox'),)

class NetboxVtpVlan(models.Model):
    netbox = models.ForeignKey('Netbox', db_column='netboxid')
    vtp_vlan = models.IntegerField(db_column='vtpvlan')
    class Meta:
        db_table = 'netbox_vtpvlan'
        unique_together = (('netbox', 'vtp_vlan'),)

class Cam(models.Model):
    id = models.IntegerField(db_column='camid', primary_key=True)
    netbox = models.ForeignKey('Netbox', db_column='netboxid')
    sysname = models.CharField(max_length=-1)
    ifindex = models.IntegerField()
    module = models.CharField(max_length=4)
    port = models.CharField(max_length=-1)
    start_time = models.DateTimeField()
    end_time = models.DateTimeField()
    miss_count = models.IntegerField(db_column='misscnt', default=0)
    mac = models.TextField() # This field type is a guess.
    class Meta:
        db_table = 'cam'
        unique_together = (('netbox', 'sysname', 'module', 'port',
                            'mac', 'start_time'),)
