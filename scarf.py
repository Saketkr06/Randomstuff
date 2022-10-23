# amt = float(input("Enter amount: "))
# interest = float(input("Enter interest"))
# period = int(input("Enter years"))
# value = 0
# year = 1
# while year<= period:
#     value = amt + amt*interest
#     print(f"Your amt in {year} is {value}")
#     amt = value
#     year = year + 1

# N = 10
# sum = 0
# n = 0
# while n<N:
#     no = float(input("enter"))
#     sum = no + sum
#     n = n+1
# avg = float(sum)/N
# print(avg)

#
# sum = 0.0
# for i in range(1,11):
#     sum = sum + 1/i
#     print(f"{i}, {sum}")



# import math
# a = int(input("enter"))
# b = int(input("enter"))
# c = int(input("enter"))
# d= b*b - 4*a*c
# if d<0:
#     print("roots are imaginary")
# else:
#     r1 = (-b + math.sqrt(d))/(2*a)
#     r2 = (-b - math.sqrt(d))/(2*a)
#     print(r1)
#     print(r2)

# #Fibonnacci Series........
# a,b = 0,1
# while b<100:
#     print(b)
#     a,b=b,a+b



# n=20
# print("-"*50)
# for i in range(1,11):
#     print(f"{n}x{i}={n*i}", end=" ")
# import math
# r = float(input("enter"))
# area = math.pi*r**2
# print(area)

# import datetime
# t=datetime.datetime.now()
# print(t)

# frst_name = input("enter")
# last_name = input("enter")
# s = frst_name + last_name
# print(s[::-1])

# a = input().split(",")
# print(a)
# b = tuple(a)
# print(b)
# file = input("file name")
# f = file.split(".")
# print(f[-1])



#
# color_list = ["Red","Green","White" ,"Black"]
#
# print(color_list[0],color_list[-1])
# n = input()
# c1 =int(n+n)
# c2 = int(n+n+n)
#
# print(int(n)+c1+c2)

# from datetime import date
# f_date= date(2022,7,2)
# l_date=date(2022,8,1)
# delta = l_date - f_date
# print(delta.days)

# import math
# r = int(input())
# v = math.pi*(4/3)*r**3
# print(v)

# a=17
# b= int(input())
# c=b-a
# if c>17:
#     print(2*c)
# else:
#     print(c)
# def difference(n):
#     if n<=17:
#         return 17-n
#     else:
#         return (n-17)*2
#
# print(difference(22))
# letter = input()
# if letter[0:2]=="is":
#     print(letter)
#
# else:
#     print("is"+letter)

# n=int(input())
# if n%2==0:
#     print("even")
#
# else:
#     print("odd")

# def count_4(nums):
#     count = 0
#     for num in nums:
#         if num == 4:
#             count +=1
#     print(count)
#
# print(count_4([1,4,6,4,7,4]))

# vowel = ["a","e","i","o","u"]
# enter_letter=input()
# if enter_letter in vowel:
#     print("it is a vowel")
#
# else:
#     print("Go on machaa")

# def check_value(v):
#     n = int(input("enter"))
#     if n in v:
#         return True
#     else:
#         return False
#
# print(check_value([1, 5, 8, 3]))
#
# def histogram(items):
#     for i in range(4):
#         print("*"*items[i])
#
# histogram([2, 3, 6, 5])

# a,b = input().split(",")
# i=1
# n=1000
# for i in range(1,n):
#     if int(a)%i==0 and int(b)%i==0:
#         i =i*1
#         print(i)
# import math
# def dist_cal(x1,y1,x2,y2):
#     y_dist=y2-y1
#     x_dist=x2-x1
#     z=x_dist**2 + y_dist**2
#     dst = math.sqrt(z)
#     print(dst)
#
# print(dist_cal(4,6,5,8))

# def principal_amt(amt,i,years):
#     sum = 0
#     m1 = amt*0.01*i*years
#     sum = sum + m1 + amt
#     return sum
#
# print(principal_amt(10000,3.5,7))
#
# def personal_details():
#     name = "Saket Kumar"
#     age = 22
#     place = "Bangalore"
#     return f"Hey {name} you are {age} years old living in {place}"
# print(personal_details())
# def add_two(a,b):
#     if not (isinstance(a,int) and isinstance(b,int)):
#         return "Enter integer"
#     return a + b
# print(add_two(10,"c"))
# def lcm(a,b):
#     if a > b:
#         z = a
#     else:
#         z = b
#     while True:
#         if z%a==0 and z%b==0:
#             lcm = z
#             break
#         z += 1
#     return lcm
# print(lcm(4,6))

# import os.path
# print(os.path.isfile('main.py'))

# import struct
# print(struct.calcsize("P")*8)

# import os
# for data in os.environ:
#     print(data)
#     print('-'*15)
#     print(os.environ[data])
#     print('='*30)










